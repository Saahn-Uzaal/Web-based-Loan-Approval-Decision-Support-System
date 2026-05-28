package com.loanapproval.dss.profile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record CustomerProfile(
    Long userId,
    String fullName,
    String phone,
    String identityNumber,
    LocalDate dateOfBirth,
    BigDecimal monthlyIncome,
    BigDecimal verifiedMonthlyIncome,
    BigDecimal debtToIncomeRatio,
    String employmentStatus,
    LocalDate employmentStartDate,
    String bankAccountNumber,
    String bankName,
    Integer creditHistoryScore,
    Integer paymentRating,
    String payslipOriginalFilename,
    String payslipStorageName,
    String payslipContentType,
    Long payslipFileSize,
    Instant payslipUploadedAt,
    String identityCardFrontOriginalFilename,
    String identityCardFrontStorageName,
    String identityCardFrontContentType,
    Long identityCardFrontFileSize,
    Instant identityCardFrontUploadedAt,
    String identityCardBackOriginalFilename,
    String identityCardBackStorageName,
    String identityCardBackContentType,
    Long identityCardBackFileSize,
    Instant identityCardBackUploadedAt
) {
    /**
     * Returns verified income if available, otherwise falls back to declared income.
     * DSS and Eligibility should use this method instead of monthlyIncome() directly.
     */
    public BigDecimal effectiveMonthlyIncome() {
        if (verifiedMonthlyIncome != null && verifiedMonthlyIncome.compareTo(BigDecimal.ZERO) > 0) {
            return verifiedMonthlyIncome;
        }
        return monthlyIncome;
    }
}
