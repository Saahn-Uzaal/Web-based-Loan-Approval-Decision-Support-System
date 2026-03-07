package com.loanapproval.dss.staff;

import com.loanapproval.dss.compliance.ComplianceAuditService;
import com.loanapproval.dss.compliance.ComplianceOutcome;
import com.loanapproval.dss.contract.LoanContractService;
import com.loanapproval.dss.loan.LoanRecord;
import com.loanapproval.dss.loan.LoanRepository;
import com.loanapproval.dss.loan.LoanStatus;
import com.loanapproval.dss.shared.PageResponse;
import com.loanapproval.dss.staff.dto.StaffDecisionRequest;
import com.loanapproval.dss.staff.dto.StaffDecisionResponse;
import com.loanapproval.dss.staff.dto.StaffRequestDetailResponse;
import com.loanapproval.dss.staff.dto.StaffRequestSummaryResponse;
import com.loanapproval.dss.verification.CustomerVerification;
import com.loanapproval.dss.verification.CustomerVerificationService;
import com.loanapproval.dss.verification.VerificationStatus;
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
        LoanStatus.WAITING_SUPERVISOR
    );

    private final StaffReviewRepository staffReviewRepository;
    private final LoanRepository loanRepository;
    private final LoanContractService loanContractService;
    private final CustomerVerificationService customerVerificationService;
    private final ComplianceAuditService complianceAuditService;

    public StaffReviewService(
        StaffReviewRepository staffReviewRepository,
        LoanRepository loanRepository,
        LoanContractService loanContractService,
        CustomerVerificationService customerVerificationService,
        ComplianceAuditService complianceAuditService
    ) {
        this.staffReviewRepository = staffReviewRepository;
        this.loanRepository = loanRepository;
        this.loanContractService = loanContractService;
        this.customerVerificationService = customerVerificationService;
        this.complianceAuditService = complianceAuditService;
    }

    public List<StaffRequestSummaryResponse> listReviewQueue(LoanStatus status) {
        if (status != null && !REVIEW_QUEUE_STATUSES.contains(status)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Status filter must be PENDING or WAITING_SUPERVISOR"
            );
        }
        return staffReviewRepository.findReviewQueue(status);
    }

    public PageResponse<StaffRequestSummaryResponse> listReviewQueuePaged(LoanStatus status, int page, int size) {
        if (status != null && !REVIEW_QUEUE_STATUSES.contains(status)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Status filter must be PENDING or WAITING_SUPERVISOR"
            );
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
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Loan request not found"));

        List<StaffRequestDetailResponse.DecisionAuditEntry> audits =
            staffReviewRepository.findDecisionAuditsByLoanRequestId(loanRequestId);

        return withAudits(detail, audits);
    }

    @Transactional
    public StaffDecisionResponse submitDecision(
        Long staffUserId,
        Long loanRequestId,
        StaffDecisionRequest request
    ) {
        LoanRecord loan = loanRepository.findById(loanRequestId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Loan request not found"));
        LoanStatus currentStatus = loan.status();

        if (currentStatus == LoanStatus.APPROVED || currentStatus == LoanStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Loan request already finalized");
        }

        LoanStatus nextStatus = toLoanStatus(request.action());
        String reason = request.reason().trim();

        if (nextStatus == LoanStatus.APPROVED) {
            CustomerVerification verification = customerVerificationService.getOrDefault(loan.customerId());
            if (verification.hasHardRejectFlag()) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot approve this request because customer failed KYC/AML/fraud checks"
                );
            }
            if (!isFullyVerified(verification)) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot approve before all verification checks are PASSED"
                );
            }
        }

        int updatedRows = staffReviewRepository.updateFinalDecision(loanRequestId, nextStatus, reason);
        if (updatedRows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Loan request not found");
        }

        staffReviewRepository.insertDecisionAudit(loanRequestId, staffUserId, request.action(), reason);

        log.info("Staff decision submitted: loanRequestId={}, staffUserId={}, action={}, newStatus={}",
            loanRequestId, staffUserId, request.action(), nextStatus);

        LoanRecord updatedLoan = loanRepository.findById(loanRequestId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Loan request not found"));
        if (updatedLoan.status() == LoanStatus.APPROVED) {
            loanContractService.createIfMissingFromApprovedLoan(updatedLoan, staffUserId);
        }

        complianceAuditService.log(
            updatedLoan.customerId(),
            updatedLoan.id(),
            staffUserId,
            actionType(nextStatus),
            actionOutcome(nextStatus),
            "action=" + request.action() + ", reason=" + reason
        );

        StaffRequestDetailResponse updated = getRequestDetail(loanRequestId);
        return new StaffDecisionResponse(
            updated.id(),
            updated.status(),
            updated.finalReason(),
            updated.updatedAt()
        );
    }

    private LoanStatus toLoanStatus(StaffDecisionAction action) {
        return switch (action) {
            case APPROVE -> LoanStatus.APPROVED;
            case REJECT -> LoanStatus.REJECTED;
            case ESCALATE -> LoanStatus.WAITING_SUPERVISOR;
        };
    }

    private String actionType(LoanStatus status) {
        return switch (status) {
            case APPROVED -> "STAFF_DECISION_APPROVE";
            case REJECTED -> "STAFF_DECISION_REJECT";
            case WAITING_SUPERVISOR -> "STAFF_DECISION_ESCALATE";
            case PENDING -> "STAFF_DECISION_PENDING";
        };
    }

    private ComplianceOutcome actionOutcome(LoanStatus status) {
        return switch (status) {
            case APPROVED -> ComplianceOutcome.PASSED;
            case REJECTED -> ComplianceOutcome.FAILED;
            case WAITING_SUPERVISOR, PENDING -> ComplianceOutcome.INFO;
        };
    }

    private boolean isFullyVerified(CustomerVerification verification) {
        return verification.documentStatus() == VerificationStatus.PASSED &&
            verification.identityStatus() == VerificationStatus.PASSED &&
            verification.incomeStatus() == VerificationStatus.PASSED &&
            verification.kycStatus() == VerificationStatus.PASSED &&
            verification.amlStatus() == VerificationStatus.PASSED &&
            !verification.fraudFlag();
    }

    private StaffRequestDetailResponse withAudits(
        StaffRequestDetailResponse detail,
        List<StaffRequestDetailResponse.DecisionAuditEntry> audits
    ) {
        return new StaffRequestDetailResponse(
            detail.id(),
            detail.status(),
            detail.amount(),
            detail.termMonths(),
            detail.purpose(),
            detail.finalReason(),
            detail.createdAt(),
            detail.updatedAt(),
            detail.customer(),
            detail.customerProfile(),
            detail.dss(),
            detail.verification(),
            detail.risk(),
            detail.contract(),
            audits
        );
    }
}
