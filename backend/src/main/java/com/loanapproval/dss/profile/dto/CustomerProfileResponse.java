package com.loanapproval.dss.profile.dto;

import java.math.BigDecimal;

public record CustomerProfileResponse(
    Long userId,
    String fullName,
    String phone,
    BigDecimal monthlyIncome,
    BigDecimal debtToIncomeRatio,
    String employmentStatus
) {
}
