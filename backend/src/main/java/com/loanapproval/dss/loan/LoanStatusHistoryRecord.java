package com.loanapproval.dss.loan;

import java.time.Instant;

public record LoanStatusHistoryRecord(
        Long id,
        Long loanRequestId,
        LoanStatus fromStatus,
        LoanStatus toStatus,
        String changeReason,
        Long changedByUserId,
        String source,
        Instant createdAt) {
}
