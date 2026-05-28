package com.loanapproval.dss.creditcheck;

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
    Instant updatedAt
) {
}
