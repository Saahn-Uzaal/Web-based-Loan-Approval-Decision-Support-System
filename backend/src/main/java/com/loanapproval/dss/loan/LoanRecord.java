package com.loanapproval.dss.loan;

import java.math.BigDecimal;
import java.time.Instant;

public record LoanRecord(
    Long id,
    Long customerId,
    BigDecimal amount,
    Integer termMonths,
    LoanPurpose purpose,
    LoanStatus status,
    String finalReason,
    Instant createdAt,
    Instant updatedAt
) {
}
