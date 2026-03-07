package com.loanapproval.dss.profile;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CustomerProfile(
    Long userId,
    String fullName,
    String phone,
    LocalDate dateOfBirth,
    BigDecimal monthlyIncome,
    BigDecimal debtToIncomeRatio,
    String employmentStatus,
    LocalDate employmentStartDate,
    Integer creditHistoryScore,
    Integer paymentRating
) {
}
