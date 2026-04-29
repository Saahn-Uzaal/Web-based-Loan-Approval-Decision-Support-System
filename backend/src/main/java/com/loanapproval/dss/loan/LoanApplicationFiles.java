package com.loanapproval.dss.loan;

import org.springframework.web.multipart.MultipartFile;

public record LoanApplicationFiles(
        MultipartFile vehicleRegistration,
        MultipartFile licensePlateImage,
        MultipartFile idCardFront,
        MultipartFile idCardBack,
        MultipartFile faceCapture) {

    public static LoanApplicationFiles empty() {
        return new LoanApplicationFiles(null, null, null, null, null);
    }
}
