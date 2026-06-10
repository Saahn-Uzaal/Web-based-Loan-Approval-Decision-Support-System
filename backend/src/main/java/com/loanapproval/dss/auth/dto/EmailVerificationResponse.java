package com.loanapproval.dss.auth.dto;

public record EmailVerificationResponse(
    String email,
    String message,
    boolean verified
) {
}
