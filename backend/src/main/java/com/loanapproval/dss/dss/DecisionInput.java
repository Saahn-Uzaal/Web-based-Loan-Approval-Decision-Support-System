package com.loanapproval.dss.dss;

import com.loanapproval.dss.loan.LoanPurpose;
import java.math.BigDecimal;

public record DecisionInput(
    Long customerId,
    BigDecimal monthlyIncome,
    BigDecimal debtToIncomeRatio,
    String employmentStatus,
    BigDecimal requestedAmount,
    Integer termMonths,
    LoanPurpose purpose
) {
}
