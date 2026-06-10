package com.loanapproval.dss.staff.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ResolveOverdueLoanRequest(
        @Min(0) @Max(180) Integer extensionDays,
        @DecimalMin(value = "0.00", inclusive = true) BigDecimal waivedLateFeeAmount,
        @NotBlank @Size(max = 500) String reason) {
}
