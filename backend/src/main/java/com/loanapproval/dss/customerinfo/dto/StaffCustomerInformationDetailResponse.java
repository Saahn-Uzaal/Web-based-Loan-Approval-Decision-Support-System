package com.loanapproval.dss.customerinfo.dto;

import com.loanapproval.dss.verification.VerificationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record StaffCustomerInformationDetailResponse(
    Long customerId,
    String email,
    Instant registeredAt,
    VerificationStatus status,
    String rejectionReason,
    String reviewedByEmail,
    Instant reviewedAt,
    ProfileSummary profile,
    List<DebtItem> debts
) {
    public record ProfileSummary(
        String fullName,
        String phone,
        LocalDate dateOfBirth,
        BigDecimal monthlyIncome,
        BigDecimal verifiedMonthlyIncome,
        BigDecimal debtToIncomeRatio,
        Integer paymentRating,
        String payslipFileName,
        Long payslipFileSize,
        Instant payslipUploadedAt
    ) {
    }

    public record DebtItem(
        Long id,
        String debtType,
        BigDecimal monthlyPayment,
        BigDecimal remainingBalance,
        String lenderName,
        String status
    ) {
    }
}
