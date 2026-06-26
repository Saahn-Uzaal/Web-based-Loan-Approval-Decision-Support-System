package com.loanapproval.dss.creditcheck;

import java.math.BigDecimal;
import java.time.Instant;

public record CreditBureauRecord(
    String identityNumber,
    String borrowerName,
    CreditBureauStatus bureauStatus,
    Integer creditScore,
    Integer activeLoanCount,
    Integer daysPastDue,
    boolean manualReviewRequired,
    boolean hardReject,
    String riskNote,
    BigDecimal totalMonthlyObligation,
    BigDecimal totalOutstandingBalance,
    BigDecimal externalMonthlyObligation,
    BigDecimal externalOutstandingBalance,
    Integer reportingInstitutionCount,
    boolean consentGranted,
    Instant lastReportedAt,
    Instant updatedAt
) {
}
