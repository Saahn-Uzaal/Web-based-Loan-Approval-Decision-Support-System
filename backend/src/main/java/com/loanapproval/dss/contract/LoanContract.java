package com.loanapproval.dss.contract;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record LoanContract(
    Long id,
    Long loanRequestId,
    Long customerId,
    BigDecimal principalAmount,
    BigDecimal annualInterestRate,
    Integer termMonths,
    LocalDate startDate,
    LocalDate endDate,
    LocalDate firstPaymentDate,
    String monthlyPaymentDay,
    LocalDate finalPaymentDate,
    BigDecimal monthlyPayment,
    BigDecimal totalInterest,
    LoanContractStatus status,
    Instant createdAt,
    Instant updatedAt
) {
}
