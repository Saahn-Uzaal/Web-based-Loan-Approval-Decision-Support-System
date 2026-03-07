package com.loanapproval.dss.verification.dto;

import com.loanapproval.dss.verification.VerificationStatus;
import java.time.Instant;

public record CustomerVerificationResponse(
    Long customerId,
    VerificationStatus documentStatus,
    VerificationStatus identityStatus,
    VerificationStatus incomeStatus,
    VerificationStatus kycStatus,
    VerificationStatus amlStatus,
    boolean fraudFlag,
    String note,
    Long verifiedBy,
    Instant verifiedAt,
    boolean hardReject,
    boolean pending
) {
}
