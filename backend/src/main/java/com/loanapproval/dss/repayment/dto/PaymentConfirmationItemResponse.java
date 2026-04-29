package com.loanapproval.dss.repayment.dto;

import com.loanapproval.dss.repayment.PaymentConfirmationStatus;
import com.loanapproval.dss.repayment.RepaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record PaymentConfirmationItemResponse(
        Long id,
        Long loanRequestId,
        BigDecimal expectedAmountDue,
        BigDecimal expectedOutstandingAmount,
        Integer expectedInstallmentNumber,
        LocalDate expectedDueDate,
        String proofFileName,
        Long proofFileSize,
        String customerNote,
        PaymentConfirmationStatus status,
        BigDecimal confirmedAmount,
        Instant confirmedPaidAt,
        String bankTransactionCode,
        RepaymentStatus repaymentStatus,
        Integer ratingDelta,
        String staffNote,
        String rejectionReason,
        Instant reviewedAt,
        Instant createdAt) {
}
