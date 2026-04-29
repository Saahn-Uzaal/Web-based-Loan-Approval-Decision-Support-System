package com.loanapproval.dss.loan.dto;

import com.loanapproval.dss.loan.LoanDocumentType;
import java.time.Instant;

public record LoanDocumentResponse(
        LoanDocumentType documentType,
        String fileName,
        Long fileSize,
        Instant uploadedAt) {
}
