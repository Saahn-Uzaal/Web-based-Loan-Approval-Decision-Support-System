package com.loanapproval.dss.staff.dto;

import com.loanapproval.dss.staff.StaffDecisionAction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public record StaffDecisionRequest(
    @NotNull StaffDecisionAction action,
    Instant scheduledAt,
    @Size(max = 255) String appointmentLocation,
    @Size(max = 500) String appointmentNote,
    @DecimalMin(value = "1.00", inclusive = true) BigDecimal approvedAmount,
    @Min(1) @Max(360) Integer approvedTermMonths,
    @DecimalMin(value = "0.00", inclusive = true) BigDecimal approvedAnnualRate
) {
}
