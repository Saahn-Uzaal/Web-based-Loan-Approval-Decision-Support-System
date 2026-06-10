package com.loanapproval.dss.repayment;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LoanRepaymentSnapshot(
        Long loanRequestId,
        BigDecimal totalRepayable,
        BigDecimal totalPaid,
        BigDecimal outstandingAmount,
        BigDecimal currentAmountDue,
        BigDecimal currentPrincipalDue,
        BigDecimal currentInterestDue,
        BigDecimal currentFeeDue,
        BigDecimal currentLateFeeDue,
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
            BigDecimal currentPrincipalDue,
            BigDecimal currentInterestDue,
            BigDecimal currentFeeDue,
            BigDecimal currentLateFeeDue,
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
                currentPrincipalDue,
                currentInterestDue,
                currentFeeDue,
                currentLateFeeDue,
                scheduledInstallmentAmount,
                installmentNumber,
                dueDate,
                fullyPaid,
                false,
                0);
    }

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
                currentAmountDue,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                scheduledInstallmentAmount,
                installmentNumber,
                dueDate,
                fullyPaid,
                false,
                0);
    }

    public LoanRepaymentSnapshot(
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
        this(
                loanRequestId,
                totalRepayable,
                totalPaid,
                outstandingAmount,
                currentAmountDue,
                currentAmountDue,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                scheduledInstallmentAmount,
                installmentNumber,
                dueDate,
                fullyPaid,
                overdue,
                overdueDays);
    }
}
