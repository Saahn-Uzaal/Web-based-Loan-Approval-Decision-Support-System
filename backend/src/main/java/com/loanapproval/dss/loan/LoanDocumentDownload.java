package com.loanapproval.dss.loan;

import org.springframework.core.io.Resource;

public record LoanDocumentDownload(
        Resource resource,
        String fileName,
        String contentType,
        long fileSize) {
}
