package com.loanapproval.dss.debt.dto;

import java.math.BigDecimal;

public record DebtMetricsResponse(
    BigDecimal monthlyIncome,
    BigDecimal totalMonthlyDebt,
    BigDecimal debtToIncomeRatio,
    BigDecimal debtServiceCoverageRatio,
    BigDecimal newLoanMonthlyPayment,
    BigDecimal projectedDebtToIncomeRatio,
    BigDecimal projectedDebtServiceCoverageRatio
) {
}
