package com.loanapproval.dss.loan.dto;

import com.loanapproval.dss.loan.LoanPurpose;
import com.loanapproval.dss.loan.LoanStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record LoanDetailResponse(
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
