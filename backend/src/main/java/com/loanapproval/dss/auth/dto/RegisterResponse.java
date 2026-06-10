package com.loanapproval.dss.auth.dto;

public record RegisterResponse(
    String email,
    String message,
    boolean verificationRequired
) {
}
