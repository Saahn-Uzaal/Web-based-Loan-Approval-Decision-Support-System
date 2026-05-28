package com.loanapproval.dss.creditcheck.dto;

import com.loanapproval.dss.creditcheck.CreditBureauStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpsertCreditBureauRecordRequest(
    @NotBlank @Size(max = 20) String identityNumber,
    @NotBlank @Size(max = 150) String borrowerName,
    @NotNull CreditBureauStatus bureauStatus,
    @NotNull @Min(0) @Max(100) Integer creditScore,
    @NotNull @Min(0) Integer activeLoanCount,
    @NotNull @Min(0) Integer daysPastDue,
    @NotNull Boolean manualReviewRequired,
    @NotNull Boolean hardReject,
    @Size(max = 500) String riskNote
) {
}
