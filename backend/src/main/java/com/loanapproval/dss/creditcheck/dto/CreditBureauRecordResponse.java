package com.loanapproval.dss.creditcheck.dto;

import com.loanapproval.dss.creditcheck.CreditBureauStatus;
import java.time.Instant;

public record CreditBureauRecordResponse(
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
