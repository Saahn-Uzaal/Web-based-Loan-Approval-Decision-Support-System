package com.loanapproval.dss.creditcheck.dto;

public record CreditBureauSyncResultResponse(
    int borrowerCount,
    int syncedLoanCount,
    int skippedBorrowerCount
) {
}
