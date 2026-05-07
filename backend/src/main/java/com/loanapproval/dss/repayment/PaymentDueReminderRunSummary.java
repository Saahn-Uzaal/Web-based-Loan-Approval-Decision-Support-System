package com.loanapproval.dss.repayment;

public record PaymentDueReminderRunSummary(
        int scannedLoans,
        int remindersSent) {
}
