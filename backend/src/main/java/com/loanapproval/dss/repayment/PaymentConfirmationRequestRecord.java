package com.loanapproval.dss.repayment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record PaymentConfirmationRequestRecord(
        Long id,
        Long loanRequestId,
        Long customerId,
        BigDecimal expectedAmountDue,
        BigDecimal expectedOutstandingAmount,
        Integer expectedInstallmentNumber,
        LocalDate expectedDueDate,
        String proofOriginalFileName,
        String proofStorageName,
        String proofContentType,
        Long proofFileSize,
        String customerNote,
        String idempotencyKey,
        PaymentConfirmationStatus status,
        Long reviewedBy,
        Instant reviewedAt,
        BigDecimal confirmedAmount,
        Instant confirmedPaidAt,
        String bankTransactionCode,
        String staffNote,
        String rejectionReason,
        Long repaymentId,
        Instant createdAt,
        Instant updatedAt) {
}
