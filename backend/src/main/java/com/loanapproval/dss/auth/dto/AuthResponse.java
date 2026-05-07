package com.loanapproval.dss.auth.dto;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    UserResponse user
) {
}
