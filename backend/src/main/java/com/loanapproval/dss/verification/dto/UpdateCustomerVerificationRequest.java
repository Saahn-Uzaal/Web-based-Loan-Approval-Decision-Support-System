package com.loanapproval.dss.verification.dto;

import com.loanapproval.dss.verification.VerificationStatus;
import jakarta.validation.constraints.Size;

public record UpdateCustomerVerificationRequest(
    VerificationStatus documentStatus,
    VerificationStatus identityStatus,
    VerificationStatus incomeStatus,
    VerificationStatus kycStatus,
    VerificationStatus amlStatus,
    Boolean fraudFlag,
    @Size(max = 500) String note
) {
}
