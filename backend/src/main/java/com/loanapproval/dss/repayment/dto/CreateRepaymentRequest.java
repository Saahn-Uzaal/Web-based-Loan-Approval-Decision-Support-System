package com.loanapproval.dss.repayment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record CreateRepaymentRequest(
    @NotNull Long loanRequestId,
    @NotNull @DecimalMin(value = "0.00", inclusive = true) BigDecimal amountPaid,
    @NotNull LocalDate dueDate,
    Instant paidAt,
    @Size(max = 255) String note
) {
}
