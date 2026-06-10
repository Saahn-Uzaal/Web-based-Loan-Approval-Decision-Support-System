package com.loanapproval.dss.loan;

import com.loanapproval.dss.compliance.ComplianceAuditService;
import com.loanapproval.dss.compliance.ComplianceOutcome;
import com.loanapproval.dss.notification.NotificationService;
import java.sql.Timestamp;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoanAdditionalInfoDeadlineService {

    private static final Logger log = LoggerFactory.getLogger(LoanAdditionalInfoDeadlineService.class);

    private final LoanRepository loanRepository;
    private final LoanStatusHistoryService loanStatusHistoryService;
    private final ComplianceAuditService complianceAuditService;
    private final NotificationService notificationService;

    public LoanAdditionalInfoDeadlineService(
            LoanRepository loanRepository,
            LoanStatusHistoryService loanStatusHistoryService,
            ComplianceAuditService complianceAuditService,
            NotificationService notificationService) {
        this.loanRepository = loanRepository;
        this.loanStatusHistoryService = loanStatusHistoryService;
        this.complianceAuditService = complianceAuditService;
        this.notificationService = notificationService;
    }

    @Transactional
    public int expireOverdueRequests(Instant evaluatedAt) {
        Instant effectiveTime = evaluatedAt != null ? evaluatedAt : Instant.now();
        Timestamp cutoff = Timestamp.from(effectiveTime);
        int expiredCount = 0;

        for (LoanRecord loan : loanRepository.findExpiredAdditionalInfoRequests(cutoff)) {
            String reason = buildExpiredReason(loan.additionalInfoRequestDeadline());
            int updated = loanRepository.expireAdditionalInfoRequest(loan.id(), reason, cutoff);
            if (updated == 0) {
                continue;
            }
            expiredCount++;
            loanStatusHistoryService.recordTransition(
                    loan,
                    LoanStatus.REJECTED,
                    null,
                    "ADDITIONAL_INFO_DEADLINE",
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
        }

        if (expiredCount > 0) {
            log.info("Expired additional-info loan requests: count={}", expiredCount);
        }
        return expiredCount;
    }

    private String buildExpiredReason(Instant deadline) {
        return "Tự động từ chối vì khách hàng không bổ sung hồ sơ trước hạn " + deadline + ".";
    }
}
