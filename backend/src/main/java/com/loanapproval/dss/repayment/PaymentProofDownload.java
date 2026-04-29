package com.loanapproval.dss.repayment;

import org.springframework.core.io.Resource;

public record PaymentProofDownload(
        Resource resource,
        String fileName,
        String contentType,
        long fileSize) {
}
