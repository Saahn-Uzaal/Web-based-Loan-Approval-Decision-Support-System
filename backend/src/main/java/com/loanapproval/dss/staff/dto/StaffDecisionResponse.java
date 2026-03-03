package com.loanapproval.dss.staff.dto;

import com.loanapproval.dss.loan.LoanStatus;
import java.time.Instant;

public record StaffDecisionResponse(
    Long loanRequestId,
    LoanStatus status,
    String finalReason,
    Instant updatedAt
) {
}
