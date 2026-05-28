package com.loanapproval.dss.profile;

import org.springframework.web.multipart.MultipartFile;

public record CustomerProfileFiles(
    MultipartFile payslip,
    MultipartFile identityCardFront,
    MultipartFile identityCardBack
) {
    public static CustomerProfileFiles empty() {
        return new CustomerProfileFiles(null, null, null);
    }
}
