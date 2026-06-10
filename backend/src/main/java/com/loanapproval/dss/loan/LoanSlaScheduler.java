package com.loanapproval.dss.loan;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LoanSlaScheduler {

    private static final Logger log = LoggerFactory.getLogger(LoanSlaScheduler.class);

    private final LoanSlaService loanSlaService;

    public LoanSlaScheduler(LoanSlaService loanSlaService) {
        this.loanSlaService = loanSlaService;
    }

    @Scheduled(
            cron = "${app.loan.sla-expiry-cron:0 0 * * * *}",
            zone = "${app.loan.sla-expiry-zone:Asia/Ho_Chi_Minh}")
    public void expireLoansPastSlaDeadline() {
        try {
            loanSlaService.expireOverdueLoans(Instant.now());
        } catch (RuntimeException ex) {
            log.error("Loan SLA expiry scan failed", ex);
        }
    }
}
