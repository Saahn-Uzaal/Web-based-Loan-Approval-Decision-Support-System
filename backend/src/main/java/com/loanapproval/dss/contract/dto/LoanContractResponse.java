package com.loanapproval.dss.contract.dto;

import com.loanapproval.dss.contract.LoanContractStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record LoanContractResponse(
    Long id,
    Long loanRequestId,
    BigDecimal principalAmount,
    BigDecimal annualInterestRate,
    Integer termMonths,
    LocalDate startDate,
    LocalDate endDate,
    BigDecimal monthlyPayment,
    BigDecimal totalInterest,
    LoanContractStatus status,
    Instant createdAt
) {
}
