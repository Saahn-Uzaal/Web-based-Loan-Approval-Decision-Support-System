package com.loanapproval.dss.auth.dto;

public record AuthResponse(
    String accessToken,
    UserResponse user
) {
}
