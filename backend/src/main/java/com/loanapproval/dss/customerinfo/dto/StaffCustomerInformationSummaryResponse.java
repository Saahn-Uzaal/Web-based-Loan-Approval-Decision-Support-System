package com.loanapproval.dss.customerinfo.dto;

import com.loanapproval.dss.verification.VerificationStatus;
import java.time.Instant;

public record StaffCustomerInformationSummaryResponse(
    Long customerId,
    String email,
    String fullName,
    String phone,
    String payslipFileName,
    Instant payslipUploadedAt,
    boolean hasProfile,
    VerificationStatus status,
    String rejectionReason,
    Instant reviewedAt
) {
}
