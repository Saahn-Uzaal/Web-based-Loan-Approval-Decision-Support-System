package com.loanapproval.dss.repayment.dto;

import com.loanapproval.dss.repayment.PaymentConfirmationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record StaffPaymentConfirmationSummaryResponse(
        Long id,
        Long loanRequestId,
        Long customerId,
        String customerEmail,
        String customerName,
        BigDecimal expectedAmountDue,
        BigDecimal expectedOutstandingAmount,
        Integer expectedInstallmentNumber,
        LocalDate expectedDueDate,
        PaymentConfirmationStatus status,
        Instant createdAt,
        Instant reviewedAt) {
}
