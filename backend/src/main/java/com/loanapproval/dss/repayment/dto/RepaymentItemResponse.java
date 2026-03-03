package com.loanapproval.dss.repayment.dto;

import com.loanapproval.dss.repayment.RepaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record RepaymentItemResponse(
    Long id,
    Long loanRequestId,
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
