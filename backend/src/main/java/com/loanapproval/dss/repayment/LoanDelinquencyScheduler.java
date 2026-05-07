package com.loanapproval.dss.repayment;

import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LoanDelinquencyScheduler {

    private static final Logger log = LoggerFactory.getLogger(LoanDelinquencyScheduler.class);

    private final LoanDelinquencyService loanDelinquencyService;

    public LoanDelinquencyScheduler(LoanDelinquencyService loanDelinquencyService) {
        this.loanDelinquencyService = loanDelinquencyService;
    }

    @Scheduled(
            cron = "${app.loan.delinquency-cron:0 15 0 * * *}",
            zone = "${app.loan.delinquency-zone:Asia/Ho_Chi_Minh}")
    public void assessDailyDelinquencies() {
        try {
            loanDelinquencyService.assessAll(LocalDate.now());
        } catch (RuntimeException ex) {
            log.error("Daily loan delinquency assessment failed", ex);
        }
    }
}
