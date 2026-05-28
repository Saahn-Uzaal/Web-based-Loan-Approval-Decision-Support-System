package com.loanapproval.dss.verification.dto;

import com.loanapproval.dss.verification.VerificationStatus;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateCustomerVerificationRequest(
    VerificationStatus documentStatus,
    VerificationStatus identityStatus,
    VerificationStatus faceMatchStatus,
    VerificationStatus incomeStatus,
    BigDecimal verifiedMonthlyIncome,
    VerificationStatus kycStatus,
    VerificationStatus amlStatus,
    Boolean fraudFlag,
    @Size(max = 500) String note
) {
}
