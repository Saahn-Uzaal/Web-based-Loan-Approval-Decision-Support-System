package com.loanapproval.dss.profile;

import com.loanapproval.dss.customerinfo.CustomerInformationVerificationService;
import com.loanapproval.dss.debt.CustomerDebtService;
import com.loanapproval.dss.profile.dto.CustomerProfileRequest;
import com.loanapproval.dss.profile.dto.CustomerProfileResponse;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CustomerProfileService {

    private final CustomerProfileRepository customerProfileRepository;
    private final CustomerDebtService customerDebtService;
    private final CustomerInformationVerificationService customerInformationVerificationService;
    private final CustomerPayslipStorageService customerPayslipStorageService;

    public CustomerProfileService(
        CustomerProfileRepository customerProfileRepository,
        CustomerDebtService customerDebtService,
        CustomerInformationVerificationService customerInformationVerificationService,
        CustomerPayslipStorageService customerPayslipStorageService
    ) {
        this.customerProfileRepository = customerProfileRepository;
        this.customerDebtService = customerDebtService;
        this.customerInformationVerificationService = customerInformationVerificationService;
        this.customerPayslipStorageService = customerPayslipStorageService;
    }

    public CustomerProfileResponse getByUserId(Long userId) {
        CustomerProfile profile = customerProfileRepository.findByUserId(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ khách hàng"));
        return toResponse(profile);
    }

    @Transactional
    public CustomerProfileResponse upsert(Long userId, CustomerProfileRequest request, MultipartFile payslipFile) {
        CustomerProfile existing = customerProfileRepository.findByUserId(userId).orElse(null);
        CustomerPayslipStorageService.StoredPayslip storedPayslip = null;

        if (payslipFile != null && !payslipFile.isEmpty()) {
            storedPayslip = customerPayslipStorageService.store(
                userId,
                payslipFile,
                existing != null ? existing.payslipStorageName() : null
            );
        }

        if (storedPayslip == null && (existing == null || existing.payslipStorageName() == null || existing.payslipStorageName().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng nộp phiếu lương trong tháng gần nhất");
        }

        CustomerProfile profile = new CustomerProfile(
            userId,
            request.fullName(),
            request.phone(),
            request.dateOfBirth(),
            request.monthlyIncome() != null ? request.monthlyIncome() : existing != null ? existing.monthlyIncome() : null,
            existing != null ? existing.verifiedMonthlyIncome() : null,
            existing != null ? existing.debtToIncomeRatio() : null,
            existing != null ? existing.employmentStatus() : null,
            existing != null ? existing.employmentStartDate() : null,
            existing != null ? existing.creditHistoryScore() : null,
            existing != null ? existing.paymentRating() : null,
            storedPayslip != null ? storedPayslip.originalFileName() : existing != null ? existing.payslipOriginalFilename() : null,
            storedPayslip != null ? storedPayslip.storageName() : existing != null ? existing.payslipStorageName() : null,
            storedPayslip != null ? storedPayslip.contentType() : existing != null ? existing.payslipContentType() : null,
            storedPayslip != null ? storedPayslip.fileSize() : existing != null ? existing.payslipFileSize() : null,
            storedPayslip != null ? storedPayslip.uploadedAt() : existing != null ? existing.payslipUploadedAt() : null
        );

        customerProfileRepository.upsert(profile);
        customerInformationVerificationService.markPending(userId);

        BigDecimal calculatedDti = customerDebtService.recalculateAndSyncDti(userId);
        return customerProfileRepository.findByUserId(userId)
            .map(saved -> calculatedDti == null ? saved : new CustomerProfile(
                saved.userId(),
                saved.fullName(),
                saved.phone(),
                saved.dateOfBirth(),
                saved.monthlyIncome(),
                saved.verifiedMonthlyIncome(),
                calculatedDti,
                saved.employmentStatus(),
                saved.employmentStartDate(),
                saved.creditHistoryScore(),
                saved.paymentRating(),
                saved.payslipOriginalFilename(),
                saved.payslipStorageName(),
                saved.payslipContentType(),
                saved.payslipFileSize(),
                saved.payslipUploadedAt()
            ))
            .map(this::toResponse)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể lưu hồ sơ khách hàng"));
    }

    public CustomerPayslipDownload downloadPayslip(Long userId) {
        CustomerProfile profile = customerProfileRepository.findByUserId(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ khách hàng"));
        return customerPayslipStorageService.load(profile);
    }

    private CustomerProfileResponse toResponse(CustomerProfile profile) {
        return new CustomerProfileResponse(
            profile.userId(),
            profile.fullName(),
            profile.phone(),
            profile.dateOfBirth(),
            profile.monthlyIncome(),
            profile.verifiedMonthlyIncome(),
            profile.debtToIncomeRatio(),
            profile.employmentStatus(),
            profile.employmentStartDate(),
            profile.creditHistoryScore(),
            profile.paymentRating(),
            profile.payslipOriginalFilename(),
            profile.payslipFileSize(),
            profile.payslipUploadedAt()
        );
    }
}
