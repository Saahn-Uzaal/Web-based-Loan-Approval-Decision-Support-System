package com.loanapproval.dss.loan;

import com.loanapproval.dss.compliance.ComplianceAuditService;
import com.loanapproval.dss.compliance.ComplianceOutcome;
import com.loanapproval.dss.contract.LoanContractService;
import com.loanapproval.dss.notification.NotificationService;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoanSlaService {

    private static final Logger log = LoggerFactory.getLogger(LoanSlaService.class);

    private final LoanRepository loanRepository;
    private final LoanStatusHistoryService loanStatusHistoryService;
    private final ComplianceAuditService complianceAuditService;
    private final NotificationService notificationService;
    private final LoanContractService loanContractService;
    private final long pendingReviewTimeoutHours;
    private final long contractAcceptanceTimeoutHours;

    public LoanSlaService(
            LoanRepository loanRepository,
            LoanStatusHistoryService loanStatusHistoryService,
            ComplianceAuditService complianceAuditService,
            NotificationService notificationService,
            LoanContractService loanContractService,
            @Value("${app.loan.pending-review-timeout-hours:72}") long pendingReviewTimeoutHours,
            @Value("${app.loan.contract-acceptance-timeout-hours:48}") long contractAcceptanceTimeoutHours) {
        this.loanRepository = loanRepository;
        this.loanStatusHistoryService = loanStatusHistoryService;
        this.complianceAuditService = complianceAuditService;
        this.notificationService = notificationService;
        this.loanContractService = loanContractService;
        this.pendingReviewTimeoutHours = pendingReviewTimeoutHours;
        this.contractAcceptanceTimeoutHours = contractAcceptanceTimeoutHours;
    }

    @Transactional
    public void schedulePendingReviewDeadline(Long loanRequestId, Instant scheduledFrom) {
        if (loanRequestId == null) {
            return;
        }
        loanRepository.updateReviewDeadline(loanRequestId, Timestamp.from(pendingReviewDeadlineFrom(scheduledFrom)));
    }

    @Transactional
    public void scheduleContractAcceptanceDeadline(Long loanRequestId, Instant scheduledFrom) {
        if (loanRequestId == null) {
            return;
        }
        loanRepository.updateContractAcceptanceDeadline(
                loanRequestId,
                Timestamp.from(contractAcceptanceDeadlineFrom(scheduledFrom)));
    }

    @Transactional
    public void clearDeadlines(Long loanRequestId) {
        if (loanRequestId == null) {
            return;
        }
        loanRepository.clearSlaDeadlines(loanRequestId);
    }

    @Transactional
    public boolean expirePendingReviewIfPastDeadline(LoanRecord loan, Instant evaluatedAt) {
        if (loan == null
                || loan.status() != LoanStatus.PENDING
                || loan.reviewDeadlineAt() == null) {
            return false;
        }
        Instant effectiveTime = evaluatedAt != null ? evaluatedAt : Instant.now();
        if (loan.reviewDeadlineAt().isAfter(effectiveTime)) {
            return false;
        }
        return expirePendingReview(loan, effectiveTime);
    }

    @Transactional
    public boolean expireContractAcceptanceIfPastDeadline(LoanRecord loan, Instant evaluatedAt) {
        if (loan == null
                || loan.status() != LoanStatus.APPROVED
                || loan.contractAcceptanceDeadlineAt() == null) {
            return false;
        }
        Instant effectiveTime = evaluatedAt != null ? evaluatedAt : Instant.now();
        if (loan.contractAcceptanceDeadlineAt().isAfter(effectiveTime)) {
            return false;
        }
        return expireApprovedAcceptance(loan, effectiveTime);
    }

    @Transactional
    public LoanSlaSweepSummary expireOverdueLoans(Instant evaluatedAt) {
        Instant effectiveTime = evaluatedAt != null ? evaluatedAt : Instant.now();
        Timestamp cutoff = Timestamp.from(effectiveTime);
        int expiredPendingReviews = 0;
        int expiredApprovedAcceptances = 0;

        for (LoanRecord loan : loanRepository.findExpiredPendingReviews(cutoff)) {
            if (expirePendingReview(loan, effectiveTime)) {
                expiredPendingReviews++;
            }
        }
        for (LoanRecord loan : loanRepository.findExpiredApprovedAcceptances(cutoff)) {
            if (expireApprovedAcceptance(loan, effectiveTime)) {
                expiredApprovedAcceptances++;
            }
        }

        if (expiredPendingReviews > 0 || expiredApprovedAcceptances > 0) {
            log.info(
                    "Loan SLA expiry scan completed: expiredPendingReviews={}, expiredApprovedAcceptances={}",
                    expiredPendingReviews,
                    expiredApprovedAcceptances);
        }
        return new LoanSlaSweepSummary(expiredPendingReviews, expiredApprovedAcceptances);
    }

    public Instant pendingReviewDeadlineFrom(Instant scheduledFrom) {
        Instant baseTime = scheduledFrom != null ? scheduledFrom : Instant.now();
        return baseTime.plus(Math.max(pendingReviewTimeoutHours, 1), ChronoUnit.HOURS);
    }

    public Instant contractAcceptanceDeadlineFrom(Instant scheduledFrom) {
        Instant baseTime = scheduledFrom != null ? scheduledFrom : Instant.now();
        return baseTime.plus(Math.max(contractAcceptanceTimeoutHours, 1), ChronoUnit.HOURS);
    }

    private boolean expirePendingReview(LoanRecord loan, Instant effectiveTime) {
        String reason = buildPendingReviewExpiredReason(loan.reviewDeadlineAt());
        int updated = loanRepository.expirePendingReview(loan.id(), reason, Timestamp.from(effectiveTime));
        if (updated == 0) {
            return false;
        }
        loanStatusHistoryService.recordTransition(
                loan,
                LoanStatus.REJECTED,
                null,
                "PENDING_REVIEW_SLA",
                reason);
        complianceAuditService.log(
                loan.customerId(),
                loan.id(),
                null,
                "LOAN_APPLICATION_AUTO_REJECTED",
                ComplianceOutcome.FAILED,
                reason);
        notificationService.notifyCustomerLoanDecisionUpdated(
                loan.id(),
                loan.customerId(),
                null,
                loan.loanType(),
                LoanStatus.REJECTED,
                reason,
                true,
                null);
        return true;
    }

    private boolean expireApprovedAcceptance(LoanRecord loan, Instant effectiveTime) {
        String reason = buildApprovedAcceptanceExpiredReason(loan.contractAcceptanceDeadlineAt());
        int updated = loanRepository.expireApprovedAcceptance(loan.id(), reason, Timestamp.from(effectiveTime));
        if (updated == 0) {
            return false;
        }
        loanContractService.cancelPendingAcceptance(loan.id());
        loanStatusHistoryService.recordTransition(
                loan,
                LoanStatus.REJECTED,
                null,
                "CONTRACT_ACCEPTANCE_SLA",
                reason);
        complianceAuditService.log(
                loan.customerId(),
                loan.id(),
                null,
                "LOAN_APPLICATION_AUTO_REJECTED",
                ComplianceOutcome.FAILED,
                reason);
        notificationService.notifyCustomerLoanDecisionUpdated(
                loan.id(),
                loan.customerId(),
                null,
                loan.loanType(),
                LoanStatus.REJECTED,
                reason,
                true,
                null);
        return true;
    }

    private String buildPendingReviewExpiredReason(Instant deadline) {
        return "Tự động hủy hồ sơ vì quá SLA thẩm định trước hạn " + deadline + ".";
    }

    private String buildApprovedAcceptanceExpiredReason(Instant deadline) {
        return "Tự động hủy hồ sơ vì khách hàng không chấp nhận hợp đồng trước hạn " + deadline + ".";
    }

    public record LoanSlaSweepSummary(
            int expiredPendingReviews,
            int expiredApprovedAcceptances) {
    }
}
