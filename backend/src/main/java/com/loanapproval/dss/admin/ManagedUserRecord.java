package com.loanapproval.dss.admin;

import com.loanapproval.dss.shared.Role;
import java.time.Instant;

public record ManagedUserRecord(
    Long id,
    String email,
    Role role,
    Instant createdAt
) {
}
