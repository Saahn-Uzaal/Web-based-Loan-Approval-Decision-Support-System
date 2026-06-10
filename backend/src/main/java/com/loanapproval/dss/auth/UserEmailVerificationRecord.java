package com.loanapproval.dss.auth;

import com.loanapproval.dss.shared.Role;
import java.time.Instant;

public record UserEmailVerificationRecord(
    Long id,
    String email,
    Role role,
    Instant emailVerifiedAt,
    Instant verificationEmailSentAt
) {
}
