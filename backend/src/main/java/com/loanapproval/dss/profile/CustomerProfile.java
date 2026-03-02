package com.loanapproval.dss.profile;

import java.math.BigDecimal;

public record CustomerProfile(
    Long userId,
    String fullName,
    String phone,
    BigDecimal monthlyIncome,
    BigDecimal debtToIncomeRatio,
    String employmentStatus
) {
}
