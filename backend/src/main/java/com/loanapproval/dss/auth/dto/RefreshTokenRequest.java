package com.loanapproval.dss.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
    @NotBlank String refreshToken
) {
}
