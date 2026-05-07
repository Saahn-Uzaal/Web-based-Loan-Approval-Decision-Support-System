package com.loanapproval.dss.loan;

import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public record LoanApplicationFiles(
        MultipartFile vehicleRegistration,
        MultipartFile licensePlateImage,
        MultipartFile idCardFront,
        MultipartFile idCardBack,
        MultipartFile faceCapture,
        List<MultipartFile> supplementalDocuments) {

    public static LoanApplicationFiles empty() {
        return new LoanApplicationFiles(null, null, null, null, null, List.of());
    }

    public boolean hasAnyFiles() {
        return hasFile(vehicleRegistration)
                || hasFile(licensePlateImage)
                || hasFile(idCardFront)
                || hasFile(idCardBack)
                || hasFile(faceCapture)
                || (supplementalDocuments != null && supplementalDocuments.stream().anyMatch(this::hasFile));
    }

    private boolean hasFile(MultipartFile file) {
        return file != null && !file.isEmpty();
    }
}
