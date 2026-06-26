package com.loanapproval.dss.creditcheck.dto;

import java.math.BigDecimal;

public record CreditBureauRegistrySummaryResponse(
    long borrowerCount,
    long badDebtCount,
    long watchlistCount,
    long totalActiveLoanCount,
    BigDecimal totalMonthlyObligation,
    BigDecimal totalOutstandingBalance
) {
}
