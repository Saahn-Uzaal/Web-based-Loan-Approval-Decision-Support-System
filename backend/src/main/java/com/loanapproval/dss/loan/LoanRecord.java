package com.loanapproval.dss.loan;

import java.math.BigDecimal;
import java.time.Instant;

public record LoanRecord(
        Long id,
        Long customerId,
        LoanType loanType,
        BigDecimal amount,
        Integer termMonths,
        LoanPurpose purpose,
        CollateralType collateralType,
        LoanStatus status,
        String finalReason,
        BigDecimal eligibleLimit,
        BigDecimal approvedAmount,
        Integer approvedTermMonths,
        BigDecimal approvedAnnualRate,
        BigDecimal approvedMonthlyPayment,
        String decisionPolicyVersion,
        String intakeNote,
        Instant createdAt,
        Instant updatedAt) {
}
