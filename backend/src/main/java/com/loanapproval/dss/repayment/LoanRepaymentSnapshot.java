package com.loanapproval.dss.repayment;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LoanRepaymentSnapshot(
        Long loanRequestId,
        BigDecimal totalRepayable,
        BigDecimal totalPaid,
        BigDecimal outstandingAmount,
        BigDecimal currentAmountDue,
        BigDecimal scheduledInstallmentAmount,
        Integer installmentNumber,
        LocalDate dueDate,
        boolean fullyPaid,
        boolean overdue,
        long overdueDays) {

    public LoanRepaymentSnapshot(
            Long loanRequestId,
            BigDecimal totalRepayable,
            BigDecimal totalPaid,
            BigDecimal outstandingAmount,
            BigDecimal currentAmountDue,
            BigDecimal scheduledInstallmentAmount,
            Integer installmentNumber,
            LocalDate dueDate,
            boolean fullyPaid) {
        this(
                loanRequestId,
                totalRepayable,
                totalPaid,
                outstandingAmount,
                currentAmountDue,
                scheduledInstallmentAmount,
                installmentNumber,
                dueDate,
                fullyPaid,
                false,
                0);
    }
}
