package com.loanapproval.dss.contract.dto;

import com.loanapproval.dss.contract.LoanInstallmentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record LoanContractInstallmentResponse(
        Integer installmentNumber,
        LocalDate dueDate,
        BigDecimal openingPrincipal,
        BigDecimal scheduledPrincipal,
        BigDecimal scheduledInterest,
        BigDecimal scheduledAmount,
        BigDecimal paidAmount,
        BigDecimal remainingAmount,
        LoanInstallmentStatus status,
        Instant lastPaidAt) {
}
