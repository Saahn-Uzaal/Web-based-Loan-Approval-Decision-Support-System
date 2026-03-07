package com.loanapproval.dss.debt;

import java.math.BigDecimal;
import java.time.Instant;

public record CustomerDebt(
    Long id,
    Long customerId,
    String debtType,
    BigDecimal monthlyPayment,
    BigDecimal remainingBalance,
    String lenderName,
    DebtStatus status,
    Instant createdAt,
    Instant updatedAt
) {
}
