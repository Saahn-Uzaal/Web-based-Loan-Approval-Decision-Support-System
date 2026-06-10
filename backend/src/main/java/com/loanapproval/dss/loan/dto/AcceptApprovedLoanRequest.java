package com.loanapproval.dss.loan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AcceptApprovedLoanRequest(
        @NotNull Boolean reviewConfirmed,
        @NotBlank String captchaToken,
        @NotNull Integer captchaAnswer) {
}
