package com.loanapproval.dss.contract;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record LoanInstallment(
        Long id,
        Long loanContractId,
        Long loanRequestId,
        Long customerId,
        Integer installmentNumber,
        LocalDate dueDate,
        BigDecimal openingPrincipal,
        BigDecimal scheduledPrincipal,
        BigDecimal scheduledInterest,
        BigDecimal scheduledFee,
        BigDecimal scheduledAmount,
        BigDecimal paidPrincipal,
        BigDecimal paidInterest,
        BigDecimal paidFee,
        BigDecimal paidAmount,
        Instant lastPaidAt,
        LoanInstallmentStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public BigDecimal remainingAmount() {
        return scheduledAmount.subtract(paidAmount).max(BigDecimal.ZERO);
    }

    public BigDecimal remainingPrincipal() {
        return scheduledPrincipal.subtract(paidPrincipal).max(BigDecimal.ZERO);
    }

    public BigDecimal remainingInterest() {
        return scheduledInterest.subtract(paidInterest).max(BigDecimal.ZERO);
    }

    public BigDecimal remainingFee() {
        return scheduledFee.subtract(paidFee).max(BigDecimal.ZERO);
    }

    public boolean fullyPaid() {
        return remainingAmount().compareTo(BigDecimal.ZERO) <= 0;
    }
}
