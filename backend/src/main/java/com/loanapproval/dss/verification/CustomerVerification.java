package com.loanapproval.dss.verification;

import java.time.Instant;

public record CustomerVerification(
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
    Instant createdAt,
    Instant updatedAt
) {
    public boolean hasHardRejectFlag() {
        return kycStatus == VerificationStatus.FAILED
            || amlStatus == VerificationStatus.FAILED
            || fraudFlag;
    }

    public boolean isPending() {
        return documentStatus == VerificationStatus.PENDING
            || identityStatus == VerificationStatus.PENDING
            || incomeStatus == VerificationStatus.PENDING
            || kycStatus == VerificationStatus.PENDING
            || amlStatus == VerificationStatus.PENDING;
    }
}
