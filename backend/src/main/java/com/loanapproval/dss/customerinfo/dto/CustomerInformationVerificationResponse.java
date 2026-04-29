package com.loanapproval.dss.customerinfo.dto;

import com.loanapproval.dss.verification.VerificationStatus;
import java.time.Instant;

public record CustomerInformationVerificationResponse(
    VerificationStatus status,
    String rejectionReason,
    Instant reviewedAt
) {
}
