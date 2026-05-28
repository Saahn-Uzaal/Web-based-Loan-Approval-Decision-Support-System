package com.loanapproval.dss.loan;

import com.loanapproval.dss.verification.VerificationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record LoanApplicationSnapshot(
        Long loanRequestId,
        Long customerId,
        String fullName,
        String phone,
        LocalDate dateOfBirth,
        BigDecimal declaredMonthlyIncome,
        BigDecimal verifiedMonthlyIncome,
        BigDecimal debtToIncomeRatio,
        String employmentStatus,
        LocalDate employmentStartDate,
        Integer creditHistoryScore,
        Integer paymentRating,
        BigDecimal activeMonthlyDebt,
        Integer activeDebtCount,
        VerificationStatus informationVerificationStatus,
        VerificationStatus documentStatus,
        VerificationStatus identityStatus,
        VerificationStatus faceMatchStatus,
        VerificationStatus incomeStatus,
        VerificationStatus kycStatus,
        VerificationStatus amlStatus,
        boolean fraudFlag,
        String verificationNote,
        Long verifiedBy,
        Instant verifiedAt,
        Instant snapshotAt) {
}
