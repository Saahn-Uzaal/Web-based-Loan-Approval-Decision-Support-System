package com.loanapproval.dss.repayment;

import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentDueReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentDueReminderScheduler.class);

    private final PaymentDueReminderService paymentDueReminderService;

    public PaymentDueReminderScheduler(PaymentDueReminderService paymentDueReminderService) {
        this.paymentDueReminderService = paymentDueReminderService;
    }

    @Scheduled(
            cron = "${app.loan.payment-due-reminder-cron:0 0 8 * * *}",
            zone = "${app.loan.payment-due-reminder-zone:Asia/Ho_Chi_Minh}")
    public void sendDailyDueSoonReminders() {
        try {
            paymentDueReminderService.sendDueSoonReminders(LocalDate.now());
        } catch (RuntimeException ex) {
            log.error("Daily payment due reminder scan failed", ex);
        }
    }
}
