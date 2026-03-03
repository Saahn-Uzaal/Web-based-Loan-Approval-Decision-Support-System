package com.loanapproval.dss.repayment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record RepaymentRecord(
    Long id,
    Long loanRequestId,
    Long customerId,
    BigDecimal amountDue,
    BigDecimal amountPaid,
    LocalDate dueDate,
    Instant paidAt,
    RepaymentStatus repaymentStatus,
    Integer ratingDelta,
    String note,
    Instant createdAt
) {
}
