package com.loanapproval.dss.debt.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateDebtRequest(
    @NotBlank @Size(max = 100) String debtType,
    @NotNull @DecimalMin(value = "0.00", inclusive = true) BigDecimal monthlyPayment,
    @DecimalMin(value = "0.00", inclusive = true) BigDecimal remainingBalance,
    @Size(max = 120) String lenderName
) {
}
