package com.loanapproval.dss.creditcheck;

import java.time.Instant;

public record CustomerCreditCheckSummary(
    String identityNumber,
    boolean matchedRecord,
    CreditBureauStatus bureauStatus,
    Integer creditScore,
    Integer activeLoanCount,
    Integer daysPastDue,
    boolean manualReviewRequired,
    boolean hardReject,
    String riskNote,
    String source,
    Instant checkedAt
) {
}
