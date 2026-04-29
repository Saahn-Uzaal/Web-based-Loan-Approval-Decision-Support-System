package com.loanapproval.dss.customerinfo;

import com.loanapproval.dss.verification.VerificationStatus;
import java.time.Instant;

public record CustomerInformationVerification(
    Long customerId,
    VerificationStatus status,
    String rejectionReason,
    Long reviewedBy,
    Instant reviewedAt,
    Instant createdAt,
    Instant updatedAt
) {
}
