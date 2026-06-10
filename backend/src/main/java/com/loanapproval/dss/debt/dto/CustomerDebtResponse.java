package com.loanapproval.dss.debt.dto;

import com.loanapproval.dss.debt.DebtStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record CustomerDebtResponse(
    Long id,
    String debtType,
    BigDecimal monthlyPayment,
    BigDecimal remainingBalance,
    String lenderName,
    DebtStatus status,
    Instant createdAt,
    Instant updatedAt
) {
}
