package com.loanapproval.dss.customerinfo.dto;

import com.loanapproval.dss.creditcheck.CustomerCreditCheckSummary;
import com.loanapproval.dss.verification.VerificationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record StaffCustomerInformationDetailResponse(
    Long customerId,
    String email,
    Instant registeredAt,
    VerificationStatus status,
    String rejectionReason,
    String reviewedByEmail,
    Instant reviewedAt,
    ProfileSummary profile
) {
    public record ProfileSummary(
        String fullName,
        String phone,
        String identityNumber,
        LocalDate dateOfBirth,
        BigDecimal monthlyIncome,
        BigDecimal verifiedMonthlyIncome,
        BigDecimal debtToIncomeRatio,
        String bankAccountNumber,
        String bankName,
        Integer creditHistoryScore,
        Integer paymentRating,
        CustomerCreditCheckSummary creditCheck,
        String payslipFileName,
        Long payslipFileSize,
        Instant payslipUploadedAt,
        String identityCardFrontFileName,
        Long identityCardFrontFileSize,
        Instant identityCardFrontUploadedAt,
        String identityCardBackFileName,
        Long identityCardBackFileSize,
        Instant identityCardBackUploadedAt
    ) {
    }
}
