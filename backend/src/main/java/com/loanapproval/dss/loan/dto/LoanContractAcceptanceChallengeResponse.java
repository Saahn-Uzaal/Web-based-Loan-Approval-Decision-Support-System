package com.loanapproval.dss.loan.dto;

import java.time.Instant;

public record LoanContractAcceptanceChallengeResponse(
        String question,
        String captchaToken,
        Instant expiresAt) {
}
