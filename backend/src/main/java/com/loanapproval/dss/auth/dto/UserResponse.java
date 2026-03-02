package com.loanapproval.dss.auth.dto;

import com.loanapproval.dss.shared.Role;

public record UserResponse(
    Long id,
    String email,
    Role role
) {
}
