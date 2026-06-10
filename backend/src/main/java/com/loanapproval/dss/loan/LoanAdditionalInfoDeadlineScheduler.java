package com.loanapproval.dss.loan;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LoanAdditionalInfoDeadlineScheduler {

    private static final Logger log = LoggerFactory.getLogger(LoanAdditionalInfoDeadlineScheduler.class);

    private final LoanAdditionalInfoDeadlineService loanAdditionalInfoDeadlineService;

    public LoanAdditionalInfoDeadlineScheduler(
            LoanAdditionalInfoDeadlineService loanAdditionalInfoDeadlineService) {
        this.loanAdditionalInfoDeadlineService = loanAdditionalInfoDeadlineService;
    }

    @Scheduled(
            cron = "${app.loan.additional-info-expiry-cron:0 0 * * * *}",
            zone = "${app.loan.additional-info-expiry-zone:Asia/Ho_Chi_Minh}")
    public void expireRequestsPastDeadline() {
        try {
            loanAdditionalInfoDeadlineService.expireOverdueRequests(Instant.now());
        } catch (RuntimeException ex) {
            log.error("Additional-info request expiry scan failed", ex);
        }
    }
}
