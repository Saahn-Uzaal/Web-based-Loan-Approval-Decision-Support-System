package com.loanapproval.dss.profile.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CustomerProfileResponse(
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
