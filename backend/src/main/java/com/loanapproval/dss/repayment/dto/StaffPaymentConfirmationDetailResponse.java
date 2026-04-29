package com.loanapproval.dss.repayment.dto;

import com.loanapproval.dss.loan.LoanStatus;
import com.loanapproval.dss.repayment.PaymentConfirmationStatus;
import com.loanapproval.dss.repayment.RepaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record StaffPaymentConfirmationDetailResponse(
        Long id,
        Long loanRequestId,
        LoanStatus loanStatus,
        Long customerId,
        String customerEmail,
        String customerName,
        BigDecimal expectedAmountDue,
        BigDecimal expectedOutstandingAmount,
        Integer expectedInstallmentNumber,
        LocalDate expectedDueDate,
        BigDecimal currentAmountDue,
        BigDecimal currentOutstandingAmount,
        Integer currentInstallmentNumber,
        LocalDate currentDueDate,
        String proofFileName,
        String proofContentType,
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
        String reviewedByEmail,
        Instant reviewedAt,
        Instant createdAt) {
}
