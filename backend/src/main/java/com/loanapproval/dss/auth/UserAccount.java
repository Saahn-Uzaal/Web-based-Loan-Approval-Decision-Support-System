package com.loanapproval.dss.auth;

import com.loanapproval.dss.shared.Role;

public record UserAccount(
    Long id,
    String email,
    String passwordHash,
    Role role
) {
}
