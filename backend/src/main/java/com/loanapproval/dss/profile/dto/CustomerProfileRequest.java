package com.loanapproval.dss.profile.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CustomerProfileRequest(
    @NotBlank @Size(max = 150) String fullName,
    @Size(max = 30) String phone,
    @DecimalMin(value = "0.00", inclusive = true) BigDecimal monthlyIncome,
    @DecimalMin(value = "0.00", inclusive = true) @DecimalMax(value = "100.00", inclusive = true) BigDecimal debtToIncomeRatio,
    @Size(max = 100) String employmentStatus
) {
}
