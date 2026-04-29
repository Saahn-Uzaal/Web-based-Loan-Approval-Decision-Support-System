package com.loanapproval.dss.loan.dto;

import com.loanapproval.dss.loan.LoanPurpose;
import com.loanapproval.dss.loan.LoanStatus;
import com.loanapproval.dss.loan.LoanType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record LoanSummaryResponse(
        Long id,
        LoanType loanType,
        BigDecimal amount,
        Integer termMonths,
        LoanPurpose purpose,
        LoanStatus status,
        String finalReason,
        BigDecimal approvedAmount,
        BigDecimal approvedMonthlyPayment,
        BigDecimal totalInterest,
        BigDecimal totalRepayableAmount,
        BigDecimal totalPaidAmount,
        BigDecimal remainingRepayableAmount,
        BigDecimal nextAmountDue,
        Integer nextInstallmentNumber,
        LocalDate nextDueDate,
        Boolean nextPaymentOverdue,
        Long nextPaymentOverdueDays,
        Instant createdAt) {
}
