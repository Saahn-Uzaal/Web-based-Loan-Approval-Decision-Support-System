package com.loanapproval.dss.admin.dto;

import com.loanapproval.dss.shared.Role;
import java.time.Instant;

public record AdminUserResponse(
    Long id,
    String email,
    Role role,
    Instant createdAt
) {
}
