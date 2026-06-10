package com.loanapproval.dss.staff;

import com.loanapproval.dss.creditcheck.CustomerCreditCheckService;
import com.loanapproval.dss.creditcheck.CustomerCreditCheckSummary;
import com.loanapproval.dss.compliance.ComplianceAuditService;
import com.loanapproval.dss.compliance.ComplianceOutcome;
import com.loanapproval.dss.contract.LoanContract;
import com.loanapproval.dss.contract.LoanContractService;
import com.loanapproval.dss.loan.LoanApplicationVerificationService;
import com.loanapproval.dss.loan.LoanApplicationPolicy;
import com.loanapproval.dss.loan.LoanDocumentDownload;
import com.loanapproval.dss.loan.LoanDocumentRecord;
import com.loanapproval.dss.loan.LoanDocumentRepository;
import com.loanapproval.dss.loan.LoanDocumentStorageService;
import com.loanapproval.dss.loan.LoanDocumentType;
import com.loanapproval.dss.loan.LoanApprovalReassessmentService;
import com.loanapproval.dss.loan.LoanRecord;
import com.loanapproval.dss.loan.LoanRepository;
import com.loanapproval.dss.loan.LoanSlaService;
import com.loanapproval.dss.loan.LoanStatus;
import com.loanapproval.dss.loan.LoanStatusHistoryService;
import com.loanapproval.dss.loan.LoanType;
import com.loanapproval.dss.loan.dto.LoanDocumentResponse;
import com.loanapproval.dss.notification.NotificationService;
import com.loanapproval.dss.profile.CustomerProfile;
import com.loanapproval.dss.profile.CustomerProfileRepository;
import com.loanapproval.dss.repayment.LoanRepaymentSnapshot;
import com.loanapproval.dss.repayment.OverdueLoanResolutionService;
import com.loanapproval.dss.repayment.RepaymentScheduleService;
import com.loanapproval.dss.shared.PageResponse;
import com.loanapproval.dss.staff.dto.StaffDecisionRequest;
import com.loanapproval.dss.staff.dto.StaffDecisionResponse;
import com.loanapproval.dss.staff.dto.ResolveOverdueLoanRequest;
import com.loanapproval.dss.staff.dto.StaffRequestDetailResponse;
import com.loanapproval.dss.staff.dto.StaffRequestSummaryResponse;
import com.loanapproval.dss.verification.CustomerVerification;
import com.loanapproval.dss.verification.dto.CustomerVerificationResponse;
import com.loanapproval.dss.verification.dto.UpdateCustomerVerificationRequest;
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
    private final LoanApplicationVerificationService loanApplicationVerificationService;
    private final CustomerCreditCheckService customerCreditCheckService;
    private final CustomerProfileRepository customerProfileRepository;
    private final ComplianceAuditService complianceAuditService;
    private final LoanApprovalReassessmentService loanApprovalReassessmentService;
    private final NotificationService notificationService;
    private final LoanStatusHistoryService loanStatusHistoryService;
    private final RepaymentScheduleService repaymentScheduleService;
    private final OverdueLoanResolutionService overdueLoanResolutionService;
    private final LoanSlaService loanSlaService;

    public StaffReviewService(
            StaffReviewRepository staffReviewRepository,
            LoanRepository loanRepository,
            LoanDocumentRepository loanDocumentRepository,
            LoanDocumentStorageService loanDocumentStorageService,
            LoanContractService loanContractService,
            LoanApplicationVerificationService loanApplicationVerificationService,
            CustomerCreditCheckService customerCreditCheckService,
            CustomerProfileRepository customerProfileRepository,
            ComplianceAuditService complianceAuditService,
            LoanApprovalReassessmentService loanApprovalReassessmentService,
            NotificationService notificationService,
            LoanStatusHistoryService loanStatusHistoryService,
            RepaymentScheduleService repaymentScheduleService,
            OverdueLoanResolutionService overdueLoanResolutionService,
            LoanSlaService loanSlaService) {
        this.staffReviewRepository = staffReviewRepository;
        this.loanRepository = loanRepository;
        this.loanDocumentRepository = loanDocumentRepository;
        this.loanDocumentStorageService = loanDocumentStorageService;
        this.loanContractService = loanContractService;
        this.loanApplicationVerificationService = loanApplicationVerificationService;
        this.customerCreditCheckService = customerCreditCheckService;
        this.customerProfileRepository = customerProfileRepository;
        this.complianceAuditService = complianceAuditService;
        this.loanApprovalReassessmentService = loanApprovalReassessmentService;
        this.notificationService = notificationService;
        this.loanStatusHistoryService = loanStatusHistoryService;
        this.repaymentScheduleService = repaymentScheduleService;
        this.overdueLoanResolutionService = overdueLoanResolutionService;
        this.loanSlaService = loanSlaService;
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
        LoanRecord loan = loanRepository.findById(loanRequestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ vay"));
        if (loanSlaService.expirePendingReviewIfPastDeadline(loan, Instant.now())
                || loanSlaService.expireContractAcceptanceIfPastDeadline(loan, Instant.now())) {
            loan = loanRepository.findById(loanRequestId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ vay"));
        }
        StaffRequestDetailResponse detail = staffReviewRepository.findRequestDetailById(loanRequestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ vay"));
        CustomerCreditCheckSummary creditCheck = detail.customer() != null
                ? customerCreditCheckService.findLatestByCustomerId(detail.customer().id()).orElse(null)
                : null;

        List<StaffRequestDetailResponse.DecisionAuditEntry> audits =
                staffReviewRepository.findDecisionAuditsByLoanRequestId(loanRequestId);
        List<LoanDocumentResponse> documents = loanDocumentRepository.findByLoanRequestId(loanRequestId).stream()
                .map(this::toDocumentResponse)
                .toList();

        return withReviewData(
                detail,
                withCreditCheck(detail.customerProfile(), creditCheck),
                documents,
                audits,
                resolveRepaymentSummary(loan));
    }

    @Transactional
    public StaffRequestDetailResponse assignCase(Long staffUserId, Long loanRequestId) {
        LoanRecord loan = loanRepository.findById(loanRequestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ vay"));
        assertNoSelfServicing(staffUserId, loan);
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
        assertNoSelfServicing(staffUserId, loan);
        LoanStatus currentStatus = loan.status();
        assertCaseAlreadyAssignedTo(staffUserId, loanRequestId);
        if (loanSlaService.expirePendingReviewIfPastDeadline(loan, Instant.now())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Hồ sơ này đã quá SLA thẩm định và vừa bị tự động hủy.");
        }

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
        String additionalInfoRequestNote = normalize(request.additionalInfoRequestNote());
        Instant additionalInfoDeadlineAt =
                request.action() == StaffDecisionAction.REQUEST_MORE_INFO ? request.additionalInfoDeadlineAt() : null;
        if (requiresAppointment) {
            validateAppointment(scheduledAt);
        }
        if (request.action() == StaffDecisionAction.REQUEST_MORE_INFO) {
            validateAdditionalInfoRequest(loan, additionalInfoRequestNote, additionalInfoDeadlineAt);
        }
        if (request.action() == StaffDecisionAction.REQUEST_MORE_INFO && additionalInfoRequestNote == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Vui lòng nhập nội dung cần khách hàng bổ sung");
        }
        String reason = buildDecisionNote(
                request.action(),
                scheduledAt,
                appointmentLocation,
                appointmentNote,
                additionalInfoRequestNote);

        CustomerVerification approvalVerification = null;
        if (request.action() == StaffDecisionAction.APPROVE) {
            CustomerVerification verification =
                    loanApplicationVerificationService.getOrDefault(loanRequestId, loan.customerId());
            if (verification.hasHardRejectFlag()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Không thể duyệt hồ sơ này vì khách hàng không đạt kiểm tra KYC/AML/gian lận");
            }
            if (!loanApplicationVerificationService.isFullyVerified(loan.loanType(), verification)) {
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

        int updatedRows;
        if (request.action() == StaffDecisionAction.REQUEST_MORE_INFO) {
            updatedRows = loanRepository.requestAdditionalInfo(
                    loanRequestId,
                    reason,
                    additionalInfoRequestNote,
                    java.sql.Timestamp.from(additionalInfoDeadlineAt));
        } else {
            updatedRows = loanRepository.updateDecision(
                    loanRequestId,
                    nextStatus,
                    reason,
                    approvedTerms != null ? approvedTerms.eligibleLimit() : null,
                    approvedTerms != null ? approvedTerms.approvedAmount() : null,
                    approvedTerms != null ? approvedTerms.approvedTermMonths() : null,
                    approvedTerms != null ? approvedTerms.approvedAnnualRate() : null,
                    approvedTerms != null ? approvedTerms.approvedMonthlyPayment() : null,
                    approvedTerms != null ? approvedTerms.decisionPolicyVersion() : null);
        }
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
            loanSlaService.scheduleContractAcceptanceDeadline(
                    updatedLoan.id(),
                    updatedLoan.updatedAt() != null ? updatedLoan.updatedAt() : Instant.now());
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
                false,
                additionalInfoDeadlineAt);
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
        assertNoSelfServicing(staffUserId, loan);
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
        ensureDisbursementAccountAvailable(loan.customerId());

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
        notificationService.notifyCustomerLoanDisbursed(
                loanRequestId,
                loan.customerId(),
                staffUserId,
                loan.approvedAmount() != null ? loan.approvedAmount() : loan.amount());
        return getRequestDetail(loanRequestId);
    }

    @Transactional
    public StaffRequestDetailResponse resolveOverdueLoan(
            Long staffUserId,
            Long loanRequestId,
            ResolveOverdueLoanRequest request) {
        LoanRecord loan = loanRepository.findById(loanRequestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ vay"));
        assertNoSelfServicing(staffUserId, loan);
        ensureCaseAssignedTo(staffUserId, loanRequestId, loan.status());
        overdueLoanResolutionService.resolve(staffUserId, loan, request);
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
        return loanApplicationVerificationService.update(loanRequestId, loan.customerId(), staffUserId, request);
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

    private StaffRequestDetailResponse withReviewData(
            StaffRequestDetailResponse detail,
            StaffRequestDetailResponse.CustomerProfileSummary customerProfile,
            List<LoanDocumentResponse> documents,
            List<StaffRequestDetailResponse.DecisionAuditEntry> audits,
            StaffRequestDetailResponse.RepaymentSummary repayment) {
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
                detail.additionalInfoRequestNote(),
                detail.additionalInfoLastRequestedAt(),
                detail.additionalInfoRequestDeadline(),
                detail.additionalInfoRequestCount(),
                detail.reviewDeadlineAt(),
                detail.contractAcceptanceDeadlineAt(),
                detail.createdAt(),
                detail.updatedAt(),
                detail.customer(),
                detail.assignment(),
                customerProfile,
                detail.dss(),
                detail.verification(),
                detail.risk(),
                detail.contract(),
                repayment,
                detail.appointment(),
                documents,
                audits);
    }

    private StaffRequestDetailResponse.RepaymentSummary resolveRepaymentSummary(LoanRecord loan) {
        if (loan == null) {
            return null;
        }
        if (loan.status() != LoanStatus.ACTIVE
                && loan.status() != LoanStatus.OVERDUE
                && loan.status() != LoanStatus.CLOSED) {
            return null;
        }
        LoanContract contract = loanContractService.findByLoanRequestId(loan.id());
        if (contract == null) {
            return null;
        }
        LoanRepaymentSnapshot snapshot = repaymentScheduleService.snapshot(loan, contract, loan.customerId());
        if (snapshot == null) {
            return null;
        }
        return new StaffRequestDetailResponse.RepaymentSummary(
                snapshot.totalRepayable(),
                snapshot.totalPaid(),
                snapshot.outstandingAmount(),
                snapshot.currentAmountDue(),
                snapshot.currentPrincipalDue(),
                snapshot.currentInterestDue(),
                snapshot.currentFeeDue(),
                snapshot.currentLateFeeDue(),
                snapshot.scheduledInstallmentAmount(),
                snapshot.installmentNumber(),
                snapshot.dueDate(),
                snapshot.fullyPaid(),
                snapshot.overdue(),
                snapshot.overdueDays());
    }

    private StaffRequestDetailResponse.CustomerProfileSummary withCreditCheck(
            StaffRequestDetailResponse.CustomerProfileSummary customerProfile,
            CustomerCreditCheckSummary creditCheck) {
        if (customerProfile == null) {
            return null;
        }
        return new StaffRequestDetailResponse.CustomerProfileSummary(
                customerProfile.fullName(),
                customerProfile.phone(),
                customerProfile.identityNumber(),
                customerProfile.monthlyIncome(),
                customerProfile.verifiedMonthlyIncome(),
                customerProfile.debtToIncomeRatio(),
                customerProfile.employmentStatus(),
                customerProfile.employmentStartDate(),
                customerProfile.bankAccountNumber(),
                customerProfile.bankName(),
                customerProfile.creditHistoryScore(),
                creditCheck,
                customerProfile.payslipFileName(),
                customerProfile.payslipFileSize(),
                customerProfile.payslipUploadedAt(),
                customerProfile.identityCardFrontFileName(),
                customerProfile.identityCardFrontFileSize(),
                customerProfile.identityCardFrontUploadedAt(),
                customerProfile.identityCardBackFileName(),
                customerProfile.identityCardBackFileSize(),
                customerProfile.identityCardBackUploadedAt());
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

    private void ensureDisbursementAccountAvailable(Long customerId) {
        CustomerProfile profile = customerProfileRepository.findByUserId(customerId).orElse(null);
        if (profile == null
                || profile.bankAccountNumber() == null
                || profile.bankAccountNumber().isBlank()
                || profile.bankName() == null
                || profile.bankName().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Khách hàng chưa cập nhật đầy đủ số tài khoản và tên ngân hàng để giải ngân");
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

    private void assertNoSelfServicing(Long staffUserId, LoanRecord loan) {
        if (staffUserId == null || loan == null || !staffUserId.equals(loan.customerId())) {
            return;
        }
        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Không thể nhận hoặc xử lý hồ sơ vay của chính tài khoản nhân viên.");
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

    private void validateAdditionalInfoRequest(
            LoanRecord loan,
            String requestNote,
            Instant deadlineAt) {
        if (requestNote == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Vui lòng nhập nội dung cần khách hàng bổ sung");
        }
        if (deadlineAt == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Vui lòng chọn hạn bổ sung hồ sơ");
        }
        if (deadlineAt.isBefore(Instant.now().minusSeconds(60))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Hạn bổ sung hồ sơ không được nằm trong quá khứ");
        }
        int currentCount = loan.additionalInfoRequestCount() != null ? loan.additionalInfoRequestCount() : 0;
        if (currentCount >= LoanApplicationPolicy.MAX_ADDITIONAL_INFO_REQUESTS) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Hồ sơ này đã dùng hết số lần yêu cầu bổ sung cho phép và không thể yêu cầu thêm.");
        }
    }

    private String buildDecisionNote(
            StaffDecisionAction action,
            Instant scheduledAt,
            String location,
            String appointmentNote,
            String additionalInfoRequestNote) {
        if (scheduledAt == null) {
            return switch (action) {
                case REQUEST_MORE_INFO -> "Yêu cầu khách hàng bổ sung hồ sơ: " + additionalInfoRequestNote;
                case APPROVE -> "Đã duyệt hồ sơ";
                case REJECT -> appointmentNote != null
                        ? "Từ chối theo kết quả thẩm định: " + appointmentNote
                        : "Từ chối theo kết quả thẩm định";
            };
        }

        StringBuilder builder = new StringBuilder("Lịch hẹn gặp mặt: ").append(scheduledAt);
        if (location != null) {
            builder.append("; địa điểm: ").append(location);
        }
        if (appointmentNote != null) {
            builder.append("; ghi chú: ").append(appointmentNote);
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

