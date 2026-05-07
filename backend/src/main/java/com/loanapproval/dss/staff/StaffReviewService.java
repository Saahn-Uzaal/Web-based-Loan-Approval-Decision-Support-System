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
import com.loanapproval.dss.loan.LoanStatusHistoryService;
import com.loanapproval.dss.loan.LoanType;
import com.loanapproval.dss.loan.dto.LoanDocumentResponse;
import com.loanapproval.dss.notification.NotificationService;
import com.loanapproval.dss.shared.PageResponse;
import com.loanapproval.dss.staff.dto.StaffDecisionRequest;
import com.loanapproval.dss.staff.dto.StaffDecisionResponse;
import com.loanapproval.dss.staff.dto.StaffRequestDetailResponse;
import com.loanapproval.dss.staff.dto.StaffRequestSummaryResponse;
import com.loanapproval.dss.verification.CustomerVerification;
import com.loanapproval.dss.verification.dto.CustomerVerificationResponse;
import com.loanapproval.dss.verification.dto.UpdateCustomerVerificationRequest;
import com.loanapproval.dss.verification.CustomerVerificationService;
import com.loanapproval.dss.verification.VerificationStatus;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.Optional;
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
            LoanStatus.NEEDS_MORE_INFO);

    private static final Set<LoanStatus> OPERATION_QUEUE_STATUSES = EnumSet.of(
            LoanStatus.APPROVED,
            LoanStatus.CONTRACTED,
            LoanStatus.ACTIVE,
            LoanStatus.OVERDUE);

    private static final Set<LoanStatus> DECISION_STATUSES = EnumSet.of(LoanStatus.PENDING);
    private static final Set<LoanStatus> VERIFICATION_EDITABLE_STATUSES =
            EnumSet.of(LoanStatus.PENDING, LoanStatus.NEEDS_MORE_INFO);
    private static final Set<LoanStatus> ASSIGNABLE_STATUSES = EnumSet.of(
            LoanStatus.PENDING,
            LoanStatus.NEEDS_MORE_INFO,
            LoanStatus.APPOINTMENT_SCHEDULED,
            LoanStatus.APPROVED,
            LoanStatus.CONTRACTED,
            LoanStatus.ACTIVE,
            LoanStatus.OVERDUE);

    private final StaffReviewRepository staffReviewRepository;
    private final LoanRepository loanRepository;
    private final LoanDocumentRepository loanDocumentRepository;
    private final LoanDocumentStorageService loanDocumentStorageService;
    private final LoanContractService loanContractService;
    private final CustomerInformationVerificationService customerInformationVerificationService;
    private final CustomerVerificationService customerVerificationService;
    private final ComplianceAuditService complianceAuditService;
    private final LoanApprovalReassessmentService loanApprovalReassessmentService;
    private final NotificationService notificationService;
    private final LoanStatusHistoryService loanStatusHistoryService;

    public StaffReviewService(
            StaffReviewRepository staffReviewRepository,
            LoanRepository loanRepository,
            LoanDocumentRepository loanDocumentRepository,
            LoanDocumentStorageService loanDocumentStorageService,
            LoanContractService loanContractService,
            CustomerInformationVerificationService customerInformationVerificationService,
            CustomerVerificationService customerVerificationService,
            ComplianceAuditService complianceAuditService,
            LoanApprovalReassessmentService loanApprovalReassessmentService,
            NotificationService notificationService,
            LoanStatusHistoryService loanStatusHistoryService) {
        this.staffReviewRepository = staffReviewRepository;
        this.loanRepository = loanRepository;
        this.loanDocumentRepository = loanDocumentRepository;
        this.loanDocumentStorageService = loanDocumentStorageService;
        this.loanContractService = loanContractService;
        this.customerInformationVerificationService = customerInformationVerificationService;
        this.customerVerificationService = customerVerificationService;
        this.complianceAuditService = complianceAuditService;
        this.loanApprovalReassessmentService = loanApprovalReassessmentService;
        this.notificationService = notificationService;
        this.loanStatusHistoryService = loanStatusHistoryService;
    }

    public List<StaffRequestSummaryResponse> listReviewQueue(LoanStatus status) {
        validateStatusFilter(status, REVIEW_QUEUE_STATUSES);
        return staffReviewRepository.findReviewQueue(status);
    }

    public PageResponse<StaffRequestSummaryResponse> listReviewQueuePaged(LoanStatus status, int page, int size) {
        validateStatusFilter(status, REVIEW_QUEUE_STATUSES);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safeOffset = Math.max(page, 0) * safeSize;
        long total = staffReviewRepository.countReviewQueue(status);
        List<StaffRequestSummaryResponse> content =
                staffReviewRepository.findReviewQueuePaged(status, safeOffset, safeSize);
        return PageResponse.of(content, Math.max(page, 0), safeSize, total);
    }

    public List<StaffRequestSummaryResponse> listOperationQueue(LoanStatus status) {
        validateStatusFilter(status, OPERATION_QUEUE_STATUSES);
        return staffReviewRepository.findOperationQueue(status);
    }

    public PageResponse<StaffRequestSummaryResponse> listOperationQueuePaged(LoanStatus status, int page, int size) {
        validateStatusFilter(status, OPERATION_QUEUE_STATUSES);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safeOffset = Math.max(page, 0) * safeSize;
        long total = staffReviewRepository.countOperationQueue(status);
        List<StaffRequestSummaryResponse> content =
                staffReviewRepository.findOperationQueuePaged(status, safeOffset, safeSize);
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
    public StaffRequestDetailResponse assignCase(Long staffUserId, Long loanRequestId) {
        LoanRecord loan = loanRepository.findById(loanRequestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ vay"));
        assertAssignableStatus(loan.status());
        int updated = staffReviewRepository.assignCase(loanRequestId, staffUserId);
        if (updated == 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Hồ sơ này đang được nhân viên khác phụ trách. Vui lòng mở hồ sơ để kiểm tra người phụ trách hiện tại.");
        }
        return getRequestDetail(loanRequestId);
    }

    @Transactional
    public StaffRequestDetailResponse releaseCase(Long staffUserId, Long loanRequestId) {
        loanRepository.findById(loanRequestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ vay"));
        int updated = staffReviewRepository.releaseCase(loanRequestId, staffUserId);
        if (updated == 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Chỉ nhân viên đang phụ trách mới có thể bỏ nhận hồ sơ này.");
        }
        return getRequestDetail(loanRequestId);
    }

    @Transactional
    public StaffDecisionResponse submitDecision(Long staffUserId, Long loanRequestId, StaffDecisionRequest request) {
        LoanRecord loan = loanRepository.findById(loanRequestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ vay"));
        LoanStatus currentStatus = loan.status();
        assertCaseAlreadyAssignedTo(staffUserId, loanRequestId);

        if (!DECISION_STATUSES.contains(currentStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hồ sơ vay này đã có kết quả cuối cùng");
        }

        LoanStatus nextStatus = switch (request.action()) {
            case APPROVE -> loan.loanType() == LoanType.SECURED
                    ? LoanStatus.APPOINTMENT_SCHEDULED
                    : LoanStatus.APPROVED;
            case REJECT -> LoanStatus.REJECTED;
            case REQUEST_MORE_INFO -> LoanStatus.NEEDS_MORE_INFO;
        };
        boolean requiresAppointment =
                request.action() == StaffDecisionAction.APPROVE && loan.loanType() == LoanType.SECURED;
        Instant scheduledAt = requiresAppointment ? request.scheduledAt() : null;
        String appointmentLocation = requiresAppointment ? normalize(request.appointmentLocation()) : null;
        String appointmentNote = normalize(request.appointmentNote());
        if (requiresAppointment) {
            validateAppointment(scheduledAt);
        }
        if (request.action() == StaffDecisionAction.REQUEST_MORE_INFO && appointmentNote == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Vui lòng nhập nội dung cần khách hàng bổ sung");
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
        loanStatusHistoryService.recordTransition(
                loan,
                nextStatus,
                staffUserId,
                "STAFF_DECISION",
                reason);

        staffReviewRepository.insertDecisionAudit(loanRequestId, staffUserId, request.action(), reason);

        log.info(
                "Staff decision submitted: loanRequestId={}, staffUserId={}, action={}, newStatus={}",
                loanRequestId,
                staffUserId,
                request.action(),
                nextStatus);

        LoanRecord updatedLoan = loanRepository.findById(loanRequestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ vay"));
        if (request.action() == StaffDecisionAction.APPROVE && updatedLoan.loanType() == LoanType.UNSECURED) {
            loanContractService.createIfMissingFromApprovedLoan(updatedLoan, staffUserId);
        }
        complianceAuditService.log(
                updatedLoan.customerId(),
                updatedLoan.id(),
                staffUserId,
                actionType(nextStatus),
                actionOutcome(nextStatus),
                "action=" + request.action() + ", appointment=" + (scheduledAt != null ? scheduledAt : "none"));
        notificationService.notifyCustomerLoanDecisionUpdated(
                loanRequestId,
                updatedLoan.customerId(),
                staffUserId,
                updatedLoan.loanType(),
                nextStatus,
                reason,
                false);
        if (requiresAppointment && scheduledAt != null) {
            notificationService.notifyCustomerAppointmentScheduled(
                    loanRequestId,
                    updatedLoan.customerId(),
                    staffUserId,
                    scheduledAt,
                    appointmentLocation);
        }

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
        String message = loan.loanType() == LoanType.SECURED
                ? "Khoản vay thế chấp phải hoàn tất hợp đồng thông qua luồng lịch hẹn và thủ tục tài sản bảo đảm."
                : "Khách hàng phải tự xem và chấp nhận điều khoản trước khi nhân viên có thể giải ngân.";
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    @Transactional
    public StaffRequestDetailResponse disburseLoan(Long staffUserId, Long loanRequestId) {
        LoanRecord loan = loanRepository.findById(loanRequestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ vay"));
        ensureCaseAssignedTo(staffUserId, loanRequestId, loan.status());
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
        loanStatusHistoryService.recordTransition(
                loan,
                LoanStatus.ACTIVE,
                staffUserId,
                "STAFF_DISBURSEMENT",
                "Staff confirmed disbursement and activated the loan");
        complianceAuditService.log(
                loan.customerId(),
                loan.id(),
                staffUserId,
                "STAFF_DISBURSE_LOAN",
                ComplianceOutcome.PASSED,
                "loan activated immediately after staff confirmed disbursement");
        return getRequestDetail(loanRequestId);
    }

    @Transactional
    public CustomerVerificationResponse updateVerification(
            Long staffUserId,
            Long loanRequestId,
            UpdateCustomerVerificationRequest request) {
        LoanRecord loan = loanRepository.findById(loanRequestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ vay"));
        ensureCaseAssignedTo(staffUserId, loanRequestId, loan.status());
        if (!VERIFICATION_EDITABLE_STATUSES.contains(loan.status())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Chỉ được cập nhật xác minh khi hồ sơ còn ở bước thẩm định hoặc đang chờ khách hàng bổ sung.");
        }
        return customerVerificationService.upsert(loan.customerId(), staffUserId, request);
    }

    public LoanDocumentDownload downloadDocument(Long loanRequestId, Long documentId) {
        loanRepository.findById(loanRequestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ vay"));
        LoanDocumentRecord document = loanDocumentRepository.findByLoanRequestIdAndDocumentId(loanRequestId, documentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy chứng từ hồ sơ vay"));
        return loanDocumentStorageService.load(document);
    }

    private String actionType(LoanStatus status) {
        return switch (status) {
            case APPOINTMENT_SCHEDULED -> "STAFF_DECISION_SCHEDULE_APPOINTMENT";
            case APPROVED -> "STAFF_DECISION_APPROVE";
            case NEEDS_MORE_INFO -> "STAFF_DECISION_REQUEST_MORE_INFO";
            case REJECTED -> "STAFF_DECISION_REJECT";
            case DRAFT, PENDING -> "STAFF_DECISION_PENDING";
            case CONTRACTED, ACTIVE, OVERDUE, CLOSED, WITHDRAWN -> "STAFF_DECISION_POST_APPROVAL";
        };
    }

    private void validateStatusFilter(LoanStatus status, Set<LoanStatus> allowedStatuses) {
        if (status != null && !allowedStatuses.contains(status)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Bộ lọc trạng thái không hợp lệ");
        }
    }

    private ComplianceOutcome actionOutcome(LoanStatus status) {
        return switch (status) {
            case APPROVED -> ComplianceOutcome.PASSED;
            case REJECTED -> ComplianceOutcome.FAILED;
            case DRAFT, PENDING, NEEDS_MORE_INFO, APPOINTMENT_SCHEDULED, CONTRACTED, ACTIVE, OVERDUE, CLOSED, WITHDRAWN -> ComplianceOutcome.INFO;
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
                detail.assignment(),
                detail.customerProfile(),
                detail.dss(),
                detail.verification(),
                detail.risk(),
                detail.contract(),
                detail.appointment(),
                documents,
                audits);
    }

    private void ensureCaseAssignedTo(Long staffUserId, Long loanRequestId, LoanStatus currentStatus) {
        assertAssignableStatus(currentStatus);
        int updated = staffReviewRepository.assignCase(loanRequestId, staffUserId);
        if (updated == 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Hồ sơ này đang được nhân viên khác phụ trách. Bạn không thể chỉnh sửa khi chưa được bàn giao.");
        }
    }

    private void assertCaseAlreadyAssignedTo(Long staffUserId, Long loanRequestId) {
        Optional<Long> assignedStaffOpt = staffReviewRepository.findAssignedStaffUserId(loanRequestId);
        if (assignedStaffOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ vay");
        }
        Long assignedStaff = assignedStaffOpt.get();
        if (assignedStaff == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Bạn cần nhận phụ trách hồ sơ trước khi ra quyết định.");
        }
        if (!assignedStaff.equals(staffUserId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Hồ sơ này đang được nhân viên khác phụ trách. Bạn không thể ra quyết định khi chưa được bàn giao.");
        }
    }

    private void assertAssignableStatus(LoanStatus status) {
        if (ASSIGNABLE_STATUSES.contains(status)) {
            return;
        }
        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Chỉ có thể nhận phụ trách các hồ sơ còn trong quá trình xử lý.");
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
                case REQUEST_MORE_INFO -> "Yêu cầu khách hàng bổ sung hồ sơ: " + note;
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
                document.id(),
                document.documentType(),
                document.originalFileName(),
                document.fileSize(),
                document.uploadedAt());
    }
}

