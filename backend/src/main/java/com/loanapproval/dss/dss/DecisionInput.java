package com.loanapproval.dss.dss;

import com.loanapproval.dss.loan.LoanPurpose;
import java.math.BigDecimal;
import java.time.LocalDate;

public record DecisionInput(
    Long customerId,
    BigDecimal monthlyIncome,
    BigDecimal debtToIncomeRatio,
    String employmentStatus,
    LocalDate dateOfBirth,
    LocalDate employmentStartDate,
    Integer creditHistoryScore,
    BigDecimal collateralValue,
    BigDecimal existingMonthlyDebt,
    BigDecimal requestedAmount,
    Integer termMonths,
    LoanPurpose purpose,
    Integer paymentRating,
    Boolean kycFailed,
    Boolean amlFailed,
    Boolean fraudFlag,
    Boolean incomeVerified
) {
}
