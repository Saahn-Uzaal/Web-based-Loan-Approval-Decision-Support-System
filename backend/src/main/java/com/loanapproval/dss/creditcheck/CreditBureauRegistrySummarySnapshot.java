package com.loanapproval.dss.creditcheck;

import java.math.BigDecimal;

public record CreditBureauRegistrySummarySnapshot(
    long borrowerCount,
    long badDebtCount,
    long watchlistCount,
    long totalActiveLoanCount,
    BigDecimal totalMonthlyObligation,
    BigDecimal totalOutstandingBalance
) {
}
