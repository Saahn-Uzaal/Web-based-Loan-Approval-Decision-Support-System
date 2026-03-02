package com.loanapproval.dss.security;

import com.loanapproval.dss.shared.Role;

public record AuthenticatedUser(
    Long id,
    String email,
    Role role
) {
}
