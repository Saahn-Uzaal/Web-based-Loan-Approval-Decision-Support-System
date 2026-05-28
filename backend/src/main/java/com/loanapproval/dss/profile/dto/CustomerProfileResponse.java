package com.loanapproval.dss.profile.dto;

import com.loanapproval.dss.creditcheck.CustomerCreditCheckSummary;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record CustomerProfileResponse(
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
