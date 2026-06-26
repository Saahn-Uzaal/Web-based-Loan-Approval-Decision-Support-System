package com.loanapproval.dss.creditcheck;

import java.math.BigDecimal;
import java.time.Instant;

public record CustomerCreditCheckSummary(
    String identityNumber,
    boolean matchedRecord,
    CreditBureauStatus bureauStatus,
    Integer creditScore,
    Integer activeLoanCount,
    Integer daysPastDue,
    BigDecimal totalMonthlyObligation,
    BigDecimal totalOutstandingBalance,
    BigDecimal externalMonthlyObligation,
    BigDecimal externalOutstandingBalance,
    Integer reportingInstitutionCount,
    boolean manualReviewRequired,
    boolean hardReject,
    String riskNote,
    String source,
    Instant checkedAt
) {
}
