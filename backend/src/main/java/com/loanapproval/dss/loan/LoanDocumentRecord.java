package com.loanapproval.dss.loan;

import java.time.Instant;

public record LoanDocumentRecord(
        Long id,
        Long loanRequestId,
        LoanDocumentType documentType,
        String originalFileName,
        String storageName,
        String contentType,
        Long fileSize,
        Instant uploadedAt) {
}
