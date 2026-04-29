package com.loanapproval.dss.profile;

import org.springframework.core.io.Resource;

public record CustomerPayslipDownload(
    Resource resource,
    String fileName,
    String contentType,
    long fileSize
) {
}
