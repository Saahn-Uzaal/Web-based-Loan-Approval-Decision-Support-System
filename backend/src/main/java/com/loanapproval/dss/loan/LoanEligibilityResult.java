package com.loanapproval.dss.loan;

import java.math.BigDecimal;

public record LoanEligibilityResult(
        BigDecimal eligibleLimit,
        BigDecimal approvedAmount,
        Integer approvedTermMonths,
        BigDecimal approvedAnnualRate,
        BigDecimal approvedMonthlyPayment,
        String decisionPolicyVersion,
        String explanation) {
}
