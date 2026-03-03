package com.loanapproval.dss.staff;

import com.loanapproval.dss.loan.LoanStatus;
import com.loanapproval.dss.staff.dto.StaffDecisionRequest;
import com.loanapproval.dss.staff.dto.StaffDecisionResponse;
import com.loanapproval.dss.staff.dto.StaffRequestDetailResponse;
import com.loanapproval.dss.staff.dto.StaffRequestSummaryResponse;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StaffReviewService {

    private static final Set<LoanStatus> REVIEW_QUEUE_STATUSES = EnumSet.of(
        LoanStatus.PENDING,
        LoanStatus.WAITING_SUPERVISOR
    );

    private final StaffReviewRepository staffReviewRepository;

    public StaffReviewService(StaffReviewRepository staffReviewRepository) {
        this.staffReviewRepository = staffReviewRepository;
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
        LoanStatus currentStatus = staffReviewRepository.findStatusByLoanRequestId(loanRequestId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Loan request not found"));

        if (currentStatus == LoanStatus.APPROVED || currentStatus == LoanStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Loan request already finalized");
        }

        LoanStatus nextStatus = toLoanStatus(request.action());
        String reason = request.reason().trim();

        int updatedRows = staffReviewRepository.updateFinalDecision(loanRequestId, nextStatus, reason);
        if (updatedRows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Loan request not found");
        }

        staffReviewRepository.insertDecisionAudit(loanRequestId, staffUserId, request.action(), reason);

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
            audits
        );
    }
}
