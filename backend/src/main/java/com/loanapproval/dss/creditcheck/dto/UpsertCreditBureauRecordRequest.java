package com.loanapproval.dss.creditcheck.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpsertCreditBureauRecordRequest(
    @NotBlank @Size(max = 20) String identityNumber,
    @NotBlank @Size(max = 150) String borrowerName,
    @NotNull Boolean consentGranted,
    @NotNull Boolean fraudSuspect,
    @Size(max = 500) String riskNote,
    @NotNull List<@Valid UpsertCreditBureauLoanAccountRequest> loanAccounts
) {
}
