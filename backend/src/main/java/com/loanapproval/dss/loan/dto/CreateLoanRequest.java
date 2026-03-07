package com.loanapproval.dss.loan.dto;

import com.loanapproval.dss.loan.LoanPurpose;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CreateLoanRequest(
    @NotNull @DecimalMin(value = "1.00", inclusive = true) BigDecimal amount,
    @NotNull @Min(1) @Max(360) Integer termMonths,
    @NotNull LoanPurpose purpose,
    @DecimalMin(value = "0.00", inclusive = true) BigDecimal collateralValue
) {
}
