package com.loanapproval.dss.profile.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record CustomerProfileResponse(
    Long userId,
    String fullName,
    String phone,
    LocalDate dateOfBirth,
    BigDecimal monthlyIncome,
    BigDecimal verifiedMonthlyIncome,
    BigDecimal debtToIncomeRatio,
    String employmentStatus,
    LocalDate employmentStartDate,
    Integer paymentRating,
    String payslipFileName,
    Long payslipFileSize,
    Instant payslipUploadedAt
) {
}
