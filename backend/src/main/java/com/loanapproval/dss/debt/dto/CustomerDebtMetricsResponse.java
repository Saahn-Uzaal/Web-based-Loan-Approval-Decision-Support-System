package com.loanapproval.dss.debt.dto;

import java.math.BigDecimal;

public record CustomerDebtMetricsResponse(
    int totalDebtCount,
    int pendingVerificationCount,
    int verifiedDebtCount,
    int rejectedDebtCount,
    BigDecimal verifiedMonthlyDebt,
    BigDecimal totalMonthlyObligation,
    BigDecimal debtToIncomeRatio
) {
}
