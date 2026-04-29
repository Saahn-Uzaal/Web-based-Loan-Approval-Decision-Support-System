package com.loanapproval.dss.staff;

import com.loanapproval.dss.compliance.ComplianceAuditService;
import com.loanapproval.dss.compliance.ComplianceOutcome;
import com.loanapproval.dss.contract.LoanContractService;
import com.loanapproval.dss.customerinfo.CustomerInformationVerificationService;
import com.loanapproval.dss.loan.LoanDocumentDownload;
import com.loanapproval.dss.loan.LoanDocumentRecord;
import com.loanapproval.dss.loan.LoanDocumentRepository;
import com.loanapproval.dss.loan.LoanDocumentStorageService;
import com.loanapproval.dss.loan.LoanDocumentType;
import com.loanapproval.dss.loan.LoanApprovalReassessmentService;
import com.loanapproval.dss.loan.LoanRecord;
import com.loanapproval.dss.loan.LoanRepository;
import com.loanapproval.dss.loan.LoanStatus;
import com.loanapproval.dss.loan.LoanType;
import com.loanapproval.dss.loan.dto.LoanDocumentResponse;
import com.loanapproval.dss.shared.PageResponse;
import com.loanapproval.dss.staff.dto.StaffDecisionRequest;
import com.loanapproval.dss.staff.dto.StaffDecisionResponse;
import com.loanapproval.dss.staff.dto.StaffRequestDetailResponse;
import com.loanapproval.dss.staff.dto.StaffRequestSummaryResponse;
import com.loanapproval.dss.verification.CustomerVerification;
import com.loanapproval.dss.verification.CustomerVerificationService;
import com.loanapproval.dss.verification.VerificationStatus;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StaffReviewService {

    private static final Logger log = LoggerFactory.getLogger(StaffReviewService.class);

    private static final Set<LoanStatus> REVIEW_QUEUE_STATUSES = EnumSet.of(
            LoanStatus.PENDING,
            LoanStatus.APPOINTMENT_SCHEDULED,
            LoanStatus.APPROVED,
            LoanStatus.CONTRACTED,
            LoanStatus.DISBURSED,
            LoanStatus.ACTIVE);

    private static final Set<LoanStatus> DECISION_STATUSES = EnumSet.of(LoanStatus.PENDING);

    private final StaffReviewRepository staffReviewRepository;
    private final LoanRepository loanRepository;
    private final LoanDocumentRepository loanDocumentRepository;
    private final LoanDocumentStorageService loanDocumentStorageService;
    private final LoanContractService loanContractService;
    private final CustomerInformationVerificationService customerInformationVerificationService;
    private final CustomerVerificationService customerVerificationService;
    private final ComplianceAuditService complianceAuditService;
    private final LoanApprovalReassessmentService loanApprovalReassessmentService;

    public StaffReviewService(
            StaffReviewRepository staffReviewRepository,
            LoanRepository loanRepository,
            LoanDocumentRepository loanDocumentRepository,
            LoanDocumentStorageService loanDocumentStorageService,
            LoanContractService loanContractService,
            CustomerInformationVerificationService customerInformationVerificationService,
            CustomerVerificationService customerVerificationService,
            ComplianceAuditService complianceAuditService,
            LoanApprovalReassessmentService loanApprovalReassessmentService) {
        this.staffReviewRepository = staffReviewRepository;
        this.loanRepository = loanRepository;
        this.loanDocumentRepository = loanDocumentRepository;
        this.loanDocumentStorageService = loanDocumentStorageService;
        this.loanContractService = loanContractService;
        this.customerInformationVerificationService = customerInformationVerificationService;
        this.customerVerificationService = customerVerificationService;
        this.complianceAuditService = complianceAuditService;
        this.loanApprovalReassessmentService = loanApprovalReassessmentService;
    }

    public List<StaffRequestSummaryResponse> listReviewQueue(LoanStatus status) {
        if (status != null && !REVIEW_QUEUE_STATUSES.contains(status)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Bộ lọc trạng thái không hợp lệ");
        }
        return staffReviewRepository.findReviewQueue(status);
    }

    public PageResponse<StaffRequestSummaryResponse> listReviewQueuePaged(LoanStatus status, int page, int size) {
        if (status != null && !REVIEW_QUEUE_STATUSES.contains(status)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Bộ lọc trạng thái không hợp lệ");
        }
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safeOffset = Math.max(page, 0) * safeSize;
        long total = staffReviewRepository.countReviewQueue(status);
        List<StaffRequestSummaryResponse> content =
                staffReviewRepository.findReviewQueuePaged(status, safeOffset, safeSize);
        return PageResponse.of(content, Math.max(page, 0), safeSize, total);
    }

    public StaffRequestDetailResponse getRequestDetail(Long loanRequestId) {
        StaffRequestDetailResponse detail = staffReviewRepository.findRequestDetailById(loanRequestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ vay"));

        List<StaffRequestDetailResponse.DecisionAuditEntry> audits =
                staffReviewRepository.findDecisionAuditsByLoanRequestId(loanRequestId);
        List<LoanDocumentResponse> documents = loanDocumentRepository.findByLoanRequestId(loanRequestId).stream()
                .map(this::toDocumentResponse)
                .toList();

        return withReviewData(detail, documents, audits);
    }

    @Transactional
    public StaffDecisionResponse submitDecision(Long staffUserId, Long loanRequestId, StaffDecisionRequest request) {
        LoanRecord loan = loanRepository.findById(loanRequestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ vay"));
        LoanStatus currentStatus = loan.status();

        if (!DECISION_STATUSES.contains(currentStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hồ sơ vay này đã có kết quả cuối cùng");
        }

        LoanStatus nextStatus = request.action() == StaffDecisionAction.APPROVE
                ? loan.loanType() == LoanType.SECURED
                        ? LoanStatus.APPOINTMENT_SCHEDULED
                        : LoanStatus.APPROVED
                : LoanStatus.REJECTED;
        boolean requiresAppointment =
                request.action() == StaffDecisionAction.APPROVE && loan.loanType() == LoanType.SECURED;
        Instant scheduledAt = requiresAppointment ? request.scheduledAt() : null;
        String appointmentLocation = requiresAppointment ? normalize(request.appointmentLocation()) : null;
        String appointmentNote = normalize(request.appointmentNote());
        if (requiresAppointment) {
            validateAppointment(scheduledAt);
        }
        String reason = buildDecisionNote(request.action(), scheduledAt, appointmentLocation, appointmentNote);

        CustomerVerification approvalVerification = null;
        if (request.action() == StaffDecisionAction.APPROVE) {
            CustomerVerification verification = customerVerificationService.getOrDefault(loan.customerId());
            if (!verification.hasHardRejectFlag() && !isFullyVerified(verification)) {
                verification = customerInformationVerificationService
                        .syncLoanApprovalVerificationFromCurrentStatus(loan.customerId());
            }
            if (verification.hasHardRejectFlag()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Không thể duyệt hồ sơ này vì khách hàng không đạt kiểm tra KYC/AML/gian lận");
            }
            if (!isFullyVerified(verification)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Không thể duyệt trước khi tất cả bước xác minh đều ở trạng thái đạt");
            }
            approvalVerification = verification;
        }

        if (requiresAppointment && scheduledAt != null) {
            staffReviewRepository.insertAppointment(
                    loanRequestId,
                    loan.customerId(),
                    staffUserId,
                    scheduledAt,
                    appointmentLocation,
                    appointmentNote);
        }

        LoanApprovalReassessmentService.ReassessmentResult approvedTerms =
                request.action() == StaffDecisionAction.APPROVE
                        ? loanApprovalReassessmentService.reassessAndPersist(
                                loan,
                                approvalVerification,
                                request.approvedAmount(),
                                request.approvedTermMonths(),
                                request.approvedAnnualRate(),
                                null,
                                false)
                        : null;

        int updatedRows = loanRepository.updateDecision(
                loanRequestId,
                nextStatus,
                reason,
                approvedTerms != null ? approvedTerms.eligibleLimit() : null,
                approvedTerms != null ? approvedTerms.approvedAmount() : null,
                approvedTerms != null ? approvedTerms.approvedTermMonths() : null,
                approvedTerms != null ? approvedTerms.approvedAnnualRate() : null,
                approvedTerms != null ? approvedTerms.approvedMonthlyPayment() : null,
                approvedTerms != null ? approvedTerms.decisionPolicyVersion() : null);
        if (updatedRows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ vay");
        }

        staffReviewRepository.insertDecisionAudit(loanRequestId, staffUserId, request.action(), reason);

        log.info(
                "Staff decision submitted: loanRequestId={}, staffUserId={}, action={}, newStatus={}",
                loanRequestId,
                staffUserId,
                request.action(),
                nextStatus);

        LoanRecord updatedLoan = loanRepository.findById(loanRequestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ vay"));
        complianceAuditService.log(
                updatedLoan.customerId(),
                updatedLoan.id(),
                staffUserId,
                actionType(nextStatus),
                actionOutcome(nextStatus),
                "action=" + request.action() + ", appointment=" + (scheduledAt != null ? scheduledAt : "none"));

        StaffRequestDetailResponse updated = getRequestDetail(loanRequestId);
        return new StaffDecisionResponse(
                updated.id(),
                updated.status(),
                updated.finalReason(),
                updated.appointment(),
                updated.updatedAt());
    }

    @Transactional
    public StaffRequestDetailResponse completeContract(Long staffUserId, Long loanRequestId) {
        LoanRecord loan = loanRepository.findById(loanRequestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ vay"));
        if (loan.loanType() == LoanType.SECURED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Khoản vay thế chấp phải hoàn tất qua luồng lịch hẹn và thủ tục thế chấp, không được hoàn tất hợp đồng trực tiếp");
        }
        if (loan.status() != LoanStatus.APPROVED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Chỉ hoàn thiện hợp đồng cho hồ sơ đã duyệt");
        }

        loanContractService.createIfMissingFromApprovedLoan(loan, staffUserId);
        loanRepository.updateStatus(loanRequestId, LoanStatus.CONTRACTED);
        complianceAuditService.log(
                loan.customerId(),
                loan.id(),
                staffUserId,
                "STAFF_COMPLETE_LOAN_CONTRACT",
                ComplianceOutcome.INFO,
                "contract completion requested by staff");
        return getRequestDetail(loanRequestId);
    }

    @Transactional
    public StaffRequestDetailResponse disburseLoan(Long staffUserId, Long loanRequestId) {
        LoanRecord loan = loanRepository.findById(loanRequestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ vay"));
        if (loan.status() != LoanStatus.CONTRACTED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Chỉ giải ngân hồ sơ đã hoàn tất hợp đồng");
        }
        if (loanContractService.findByLoanRequestId(loanRequestId) == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Không thể giải ngân khi chưa có hợp đồng vay");
        }

        loanRepository.updateStatus(loanRequestId, LoanStatus.ACTIVE);
        complianceAuditService.log(
                loan.customerId(),
                loan.id(),
                staffUserId,
                "STAFF_DISBURSE_LOAN",
                ComplianceOutcome.PASSED,
                "loan marked as active after disbursement");
        return getRequestDetail(loanRequestId);
    }

    public LoanDocumentDownload downloadDocument(Long loanRequestId, LoanDocumentType documentType) {
        loanRepository.findById(loanRequestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ vay"));
        LoanDocumentRecord document = loanDocumentRepository.findByLoanRequestIdAndType(loanRequestId, documentType)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy chứng từ hồ sơ vay"));
        return loanDocumentStorageService.load(document);
    }

    private String actionType(LoanStatus status) {
        return switch (status) {
            case APPOINTMENT_SCHEDULED -> "STAFF_DECISION_SCHEDULE_APPOINTMENT";
            case APPROVED -> "STAFF_DECISION_APPROVE";
            case REJECTED -> "STAFF_DECISION_REJECT";
            case PENDING -> "STAFF_DECISION_PENDING";
            case CONTRACTED, DISBURSED, ACTIVE, CLOSED -> "STAFF_DECISION_POST_APPROVAL";
        };
    }

    private ComplianceOutcome actionOutcome(LoanStatus status) {
        return switch (status) {
            case APPROVED -> ComplianceOutcome.PASSED;
            case REJECTED -> ComplianceOutcome.FAILED;
            case PENDING, APPOINTMENT_SCHEDULED, CONTRACTED, DISBURSED, ACTIVE, CLOSED -> ComplianceOutcome.INFO;
        };
    }

    private boolean isFullyVerified(CustomerVerification verification) {
        return verification.documentStatus() == VerificationStatus.PASSED
                && verification.identityStatus() == VerificationStatus.PASSED
                && verification.faceMatchStatus() == VerificationStatus.PASSED
                && verification.incomeStatus() == VerificationStatus.PASSED
                && verification.kycStatus() == VerificationStatus.PASSED
                && verification.amlStatus() == VerificationStatus.PASSED
                && !verification.fraudFlag();
    }

    private StaffRequestDetailResponse withReviewData(
            StaffRequestDetailResponse detail,
            List<LoanDocumentResponse> documents,
            List<StaffRequestDetailResponse.DecisionAuditEntry> audits) {
        return new StaffRequestDetailResponse(
                detail.id(),
                detail.loanType(),
                detail.status(),
                detail.amount(),
                detail.termMonths(),
                detail.purpose(),
                detail.collateralType(),
                detail.finalReason(),
                detail.eligibleLimit(),
                detail.approvedAmount(),
                detail.approvedTermMonths(),
                detail.approvedAnnualRate(),
                detail.approvedMonthlyPayment(),
                detail.decisionPolicyVersion(),
                detail.intakeNote(),
                detail.createdAt(),
                detail.updatedAt(),
                detail.customer(),
                detail.customerProfile(),
                detail.dss(),
                detail.verification(),
                detail.risk(),
                detail.contract(),
                detail.appointment(),
                documents,
                audits);
    }

    private void validateAppointment(Instant scheduledAt) {
        if (scheduledAt == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Vui lòng chọn lịch hẹn gặp mặt khách hàng");
        }
        if (scheduledAt.isBefore(Instant.now().minusSeconds(60))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Lịch hẹn không được nằm trong quá khứ");
        }
    }

    private String buildDecisionNote(StaffDecisionAction action, Instant scheduledAt, String location, String note) {
        if (scheduledAt == null) {
            return switch (action) {
                case APPROVE -> "Đã duyệt hồ sơ";
                case REJECT -> note != null
                        ? "Từ chối theo kết quả thẩm định: " + note
                        : "Từ chối theo kết quả thẩm định";
            };
        }

        StringBuilder builder = new StringBuilder("Lịch hẹn gặp mặt: ").append(scheduledAt);
        if (location != null) {
            builder.append("; địa điểm: ").append(location);
        }
        if (note != null) {
            builder.append("; ghi chú: ").append(note);
        }
        return builder.toString();
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private LoanDocumentResponse toDocumentResponse(LoanDocumentRecord document) {
        return new LoanDocumentResponse(
                document.documentType(),
                document.originalFileName(),
                document.fileSize(),
                document.uploadedAt());
    }
}

