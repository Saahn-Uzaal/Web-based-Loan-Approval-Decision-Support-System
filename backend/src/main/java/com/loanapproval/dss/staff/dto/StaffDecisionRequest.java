package com.loanapproval.dss.staff.dto;

import com.loanapproval.dss.staff.StaffDecisionAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StaffDecisionRequest(
    @NotNull StaffDecisionAction action,
    @NotBlank @Size(max = 1000) String reason
) {
}
