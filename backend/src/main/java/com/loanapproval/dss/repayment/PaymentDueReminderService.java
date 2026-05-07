package com.loanapproval.dss.repayment;

import com.loanapproval.dss.notification.NotificationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentDueReminderService {

    private static final Logger log = LoggerFactory.getLogger(PaymentDueReminderService.class);

    private final LoanDelinquencyRepository loanDelinquencyRepository;
    private final RepaymentScheduleService repaymentScheduleService;
    private final PaymentDueReminderRepository paymentDueReminderRepository;
    private final NotificationService notificationService;
    private final int reminderWindowDays;

    public PaymentDueReminderService(
            LoanDelinquencyRepository loanDelinquencyRepository,
            RepaymentScheduleService repaymentScheduleService,
            PaymentDueReminderRepository paymentDueReminderRepository,
            NotificationService notificationService,
            @Value("${app.loan.payment-due-reminder-days:3}") int reminderWindowDays) {
        this.loanDelinquencyRepository = loanDelinquencyRepository;
        this.repaymentScheduleService = repaymentScheduleService;
        this.paymentDueReminderRepository = paymentDueReminderRepository;
        this.notificationService = notificationService;
        this.reminderWindowDays = Math.max(0, reminderWindowDays);
    }

    @Transactional
    public PaymentDueReminderRunSummary sendDueSoonReminders(LocalDate assessmentDate) {
        LocalDate today = assessmentDate != null ? assessmentDate : LocalDate.now();
        LocalDate maxDueDate = today.plusDays(reminderWindowDays);
        int scannedLoans = 0;
        int remindersSent = 0;

        for (LoanDelinquencyCandidate candidate : loanDelinquencyRepository.findActiveCandidates()) {
            scannedLoans++;
            LoanRepaymentSnapshot snapshot = repaymentScheduleService.snapshot(
                    candidate.loan(),
                    candidate.contract(),
                    candidate.loan().customerId(),
                    today);
            if (!shouldRemind(snapshot, today, maxDueDate)) {
                continue;
            }

            boolean created = paymentDueReminderRepository.createIfMissing(
                    candidate.loan().id(),
                    candidate.loan().customerId(),
                    snapshot.installmentNumber(),
                    snapshot.dueDate(),
                    snapshot.currentAmountDue());
            if (!created) {
                continue;
            }

            notificationService.notifyCustomerPaymentDueSoon(
                    candidate.loan().id(),
                    candidate.loan().customerId(),
                    snapshot.installmentNumber(),
                    snapshot.dueDate(),
                    snapshot.currentAmountDue(),
                    snapshot.outstandingAmount());
            remindersSent++;
        }

        PaymentDueReminderRunSummary summary = new PaymentDueReminderRunSummary(scannedLoans, remindersSent);
        log.info(
                "Payment due reminder scan finished: scannedLoans={}, remindersSent={}",
                summary.scannedLoans(),
                summary.remindersSent());
        return summary;
    }

    private boolean shouldRemind(LoanRepaymentSnapshot snapshot, LocalDate today, LocalDate maxDueDate) {
        return snapshot != null
                && !snapshot.fullyPaid()
                && !snapshot.overdue()
                && snapshot.dueDate() != null
                && snapshot.currentAmountDue() != null
                && snapshot.currentAmountDue().compareTo(BigDecimal.ZERO) > 0
                && !snapshot.dueDate().isBefore(today)
                && !snapshot.dueDate().isAfter(maxDueDate);
    }
}
