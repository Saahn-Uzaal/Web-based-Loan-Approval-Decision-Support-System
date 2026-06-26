package com.loanapproval.dss.profile;

import com.loanapproval.dss.creditcheck.CustomerCreditCheckService;
import com.loanapproval.dss.creditcheck.CustomerCreditCheckSummary;
import com.loanapproval.dss.customerinfo.CustomerInformationVerificationService;
import com.loanapproval.dss.debt.CustomerDebtService;
import com.loanapproval.dss.profile.dto.CustomerProfileRequest;
import com.loanapproval.dss.profile.dto.CustomerProfileResponse;
import java.math.BigDecimal;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CustomerProfileService {

    private final CustomerProfileRepository customerProfileRepository;
    private final CustomerDebtService customerDebtService;
    private final CustomerInformationVerificationService customerInformationVerificationService;
    private final CustomerPayslipStorageService customerPayslipStorageService;
    private final CustomerIdentityCardStorageService customerIdentityCardStorageService;
    private final CustomerCreditCheckService customerCreditCheckService;

    public CustomerProfileService(
        CustomerProfileRepository customerProfileRepository,
        CustomerDebtService customerDebtService,
        CustomerInformationVerificationService customerInformationVerificationService,
        CustomerPayslipStorageService customerPayslipStorageService,
        CustomerIdentityCardStorageService customerIdentityCardStorageService,
        CustomerCreditCheckService customerCreditCheckService
    ) {
        this.customerProfileRepository = customerProfileRepository;
        this.customerDebtService = customerDebtService;
        this.customerInformationVerificationService = customerInformationVerificationService;
        this.customerPayslipStorageService = customerPayslipStorageService;
        this.customerIdentityCardStorageService = customerIdentityCardStorageService;
        this.customerCreditCheckService = customerCreditCheckService;
    }

    public CustomerProfileResponse getByUserId(Long userId) {
        CustomerProfile profile = customerProfileRepository.findByUserId(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ khách hàng"));
        BigDecimal calculatedDti = customerDebtService.recalculateAndSyncDti(userId);
        if (calculatedDti != null) {
            profile = withCalculatedDti(profile, calculatedDti);
        }
        return toResponse(profile);
    }

    @Transactional
    public CustomerProfileResponse upsert(Long userId, CustomerProfileRequest request, CustomerProfileFiles files) {
        CustomerProfile existing = customerProfileRepository.findByUserId(userId).orElse(null);
        CustomerProfileFiles safeFiles = files != null ? files : CustomerProfileFiles.empty();
        String fullName = normalizeNullable(request.fullName());
        String phone = normalizeNullable(request.phone());
        String identityNumber = normalizeIdentityNumber(request.identityNumber());
        BigDecimal monthlyIncome = request.monthlyIncome() != null
            ? request.monthlyIncome()
            : existing != null ? existing.monthlyIncome() : null;
        String employmentStatus = normalizeEmploymentStatus(request.employmentStatus());
        var employmentStartDate = request.employmentStartDate();
        String bankAccountNumber = normalizeBankAccountNumber(request.bankAccountNumber());
        String bankName = normalizeNullable(request.bankName());
        CustomerPayslipStorageService.StoredPayslip storedPayslip = null;
        CustomerIdentityCardStorageService.StoredIdentityCard storedIdentityCardFront = null;
        CustomerIdentityCardStorageService.StoredIdentityCard storedIdentityCardBack = null;

        validateDisbursementAccount(bankAccountNumber, bankName);

        if (safeFiles.payslip() != null && !safeFiles.payslip().isEmpty()) {
            storedPayslip = customerPayslipStorageService.store(
                userId,
                safeFiles.payslip(),
                existing != null ? existing.payslipStorageName() : null
            );
        }
        if (safeFiles.identityCardFront() != null && !safeFiles.identityCardFront().isEmpty()) {
            storedIdentityCardFront = customerIdentityCardStorageService.store(
                userId,
                CustomerIdentityCardSide.FRONT,
                safeFiles.identityCardFront(),
                existing != null ? existing.identityCardFrontStorageName() : null
            );
        }
        if (safeFiles.identityCardBack() != null && !safeFiles.identityCardBack().isEmpty()) {
            storedIdentityCardBack = customerIdentityCardStorageService.store(
                userId,
                CustomerIdentityCardSide.BACK,
                safeFiles.identityCardBack(),
                existing != null ? existing.identityCardBackStorageName() : null
            );
        }

        if (storedPayslip == null && (existing == null || existing.payslipStorageName() == null || existing.payslipStorageName().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng nộp phiếu lương trong tháng gần nhất");
        }
        if (storedIdentityCardFront == null && (existing == null || existing.identityCardFrontStorageName() == null || existing.identityCardFrontStorageName().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng tải ảnh CCCD mặt trước");
        }
        if (storedIdentityCardBack == null && (existing == null || existing.identityCardBackStorageName() == null || existing.identityCardBackStorageName().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng tải ảnh CCCD mặt sau");
        }

        boolean shouldResetVerification = shouldResetInformationVerification(
            existing,
            fullName,
            phone,
            identityNumber,
            request.dateOfBirth(),
            monthlyIncome,
            employmentStatus,
            employmentStartDate,
            storedPayslip,
            storedIdentityCardFront,
            storedIdentityCardBack
        );
        boolean shouldClearVerifiedIncome = shouldClearVerifiedMonthlyIncome(
            existing,
            monthlyIncome,
            storedPayslip
        );

        CustomerProfile profile = new CustomerProfile(
            userId,
            fullName,
            phone,
            identityNumber,
            request.dateOfBirth(),
            monthlyIncome,
            existing != null ? existing.verifiedMonthlyIncome() : null,
            existing != null ? existing.debtToIncomeRatio() : null,
            employmentStatus,
            employmentStartDate,
            bankAccountNumber,
            bankName,
            existing != null ? existing.creditHistoryScore() : null,
            existing != null ? existing.paymentRating() : null,
            storedPayslip != null ? storedPayslip.originalFileName() : existing != null ? existing.payslipOriginalFilename() : null,
            storedPayslip != null ? storedPayslip.storageName() : existing != null ? existing.payslipStorageName() : null,
            storedPayslip != null ? storedPayslip.contentType() : existing != null ? existing.payslipContentType() : null,
            storedPayslip != null ? storedPayslip.fileSize() : existing != null ? existing.payslipFileSize() : null,
            storedPayslip != null ? storedPayslip.uploadedAt() : existing != null ? existing.payslipUploadedAt() : null,
            storedIdentityCardFront != null ? storedIdentityCardFront.originalFileName() : existing != null ? existing.identityCardFrontOriginalFilename() : null,
            storedIdentityCardFront != null ? storedIdentityCardFront.storageName() : existing != null ? existing.identityCardFrontStorageName() : null,
            storedIdentityCardFront != null ? storedIdentityCardFront.contentType() : existing != null ? existing.identityCardFrontContentType() : null,
            storedIdentityCardFront != null ? storedIdentityCardFront.fileSize() : existing != null ? existing.identityCardFrontFileSize() : null,
            storedIdentityCardFront != null ? storedIdentityCardFront.uploadedAt() : existing != null ? existing.identityCardFrontUploadedAt() : null,
            storedIdentityCardBack != null ? storedIdentityCardBack.originalFileName() : existing != null ? existing.identityCardBackOriginalFilename() : null,
            storedIdentityCardBack != null ? storedIdentityCardBack.storageName() : existing != null ? existing.identityCardBackStorageName() : null,
            storedIdentityCardBack != null ? storedIdentityCardBack.contentType() : existing != null ? existing.identityCardBackContentType() : null,
            storedIdentityCardBack != null ? storedIdentityCardBack.fileSize() : existing != null ? existing.identityCardBackFileSize() : null,
            storedIdentityCardBack != null ? storedIdentityCardBack.uploadedAt() : existing != null ? existing.identityCardBackUploadedAt() : null
        );

        try {
            customerProfileRepository.upsert(profile);
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Số CCCD này đã được sử dụng bởi một tài khoản khác",
                ex
            );
        }
        if (shouldResetVerification) {
            if (shouldClearVerifiedIncome) {
                customerProfileRepository.clearVerifiedMonthlyIncome(userId);
                customerInformationVerificationService.markPending(userId);
            } else {
                customerInformationVerificationService.markPending(
                    userId,
                    existing != null ? existing.verifiedMonthlyIncome() : null
                );
            }
        }
        CustomerCreditCheckSummary refreshedCreditCheck = customerCreditCheckService.refreshForCustomer(userId, profile);

        BigDecimal calculatedDti = customerDebtService.recalculateAndSyncDti(userId);
        return customerProfileRepository.findByUserId(userId)
            .map(saved -> calculatedDti == null ? saved : withCalculatedDti(saved, calculatedDti))
            .map(saved -> toResponse(saved, refreshedCreditCheck))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể lưu hồ sơ khách hàng"));
    }

    public CustomerPayslipDownload downloadPayslip(Long userId) {
        CustomerProfile profile = customerProfileRepository.findByUserId(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ khách hàng"));
        return customerPayslipStorageService.load(profile);
    }

    public CustomerIdentityCardDownload downloadIdentityCard(Long userId, CustomerIdentityCardSide side) {
        CustomerProfile profile = customerProfileRepository.findByUserId(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ khách hàng"));
        return customerIdentityCardStorageService.load(profile, side);
    }

    private CustomerProfileResponse toResponse(CustomerProfile profile) {
        return toResponse(profile, customerCreditCheckService.findLatestByCustomerId(profile.userId()).orElse(null));
    }

    private CustomerProfileResponse toResponse(CustomerProfile profile, CustomerCreditCheckSummary creditCheck) {
        return new CustomerProfileResponse(
            profile.userId(),
            profile.fullName(),
            profile.phone(),
            profile.identityNumber(),
            profile.dateOfBirth(),
            profile.monthlyIncome(),
            profile.verifiedMonthlyIncome(),
            profile.debtToIncomeRatio(),
            profile.employmentStatus(),
            profile.employmentStartDate(),
            profile.bankAccountNumber(),
            profile.bankName(),
            profile.creditHistoryScore(),
            profile.paymentRating(),
            creditCheck,
            profile.payslipOriginalFilename(),
            profile.payslipFileSize(),
            profile.payslipUploadedAt(),
            profile.identityCardFrontOriginalFilename(),
            profile.identityCardFrontFileSize(),
            profile.identityCardFrontUploadedAt(),
            profile.identityCardBackOriginalFilename(),
            profile.identityCardBackFileSize(),
            profile.identityCardBackUploadedAt()
        );
    }

    private String normalizeEmploymentStatus(String value) {
        EmploymentStatus status = EmploymentStatus.fromInput(value);
        return status != null ? status.name() : null;
    }

    private String normalizeIdentityNumber(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("\\s+", "").trim();
    }

    private String normalizeBankAccountNumber(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replaceAll("\\s+", "").trim();
        return normalized.isBlank() ? null : normalized;
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private void validateDisbursementAccount(String bankAccountNumber, String bankName) {
        boolean hasAccountNumber = bankAccountNumber != null;
        boolean hasBankName = bankName != null;
        if (hasAccountNumber != hasBankName) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Vui lòng nhập đầy đủ số tài khoản và tên ngân hàng để phục vụ giải ngân"
            );
        }
        if (bankAccountNumber != null && !bankAccountNumber.matches("\\d{6,30}")) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Số tài khoản ngân hàng phải gồm từ 6 đến 30 chữ số"
            );
        }
    }

    private CustomerProfile withCalculatedDti(CustomerProfile saved, BigDecimal calculatedDti) {
        return new CustomerProfile(
            saved.userId(),
            saved.fullName(),
            saved.phone(),
            saved.identityNumber(),
            saved.dateOfBirth(),
            saved.monthlyIncome(),
            saved.verifiedMonthlyIncome(),
            calculatedDti,
            saved.employmentStatus(),
            saved.employmentStartDate(),
            saved.bankAccountNumber(),
            saved.bankName(),
            saved.creditHistoryScore(),
            saved.paymentRating(),
            saved.payslipOriginalFilename(),
            saved.payslipStorageName(),
            saved.payslipContentType(),
            saved.payslipFileSize(),
            saved.payslipUploadedAt(),
            saved.identityCardFrontOriginalFilename(),
            saved.identityCardFrontStorageName(),
            saved.identityCardFrontContentType(),
            saved.identityCardFrontFileSize(),
            saved.identityCardFrontUploadedAt(),
            saved.identityCardBackOriginalFilename(),
            saved.identityCardBackStorageName(),
            saved.identityCardBackContentType(),
            saved.identityCardBackFileSize(),
            saved.identityCardBackUploadedAt()
        );
    }

    private boolean shouldResetInformationVerification(
        CustomerProfile existing,
        String fullName,
        String phone,
        String identityNumber,
        java.time.LocalDate dateOfBirth,
        BigDecimal monthlyIncome,
        String employmentStatus,
        java.time.LocalDate employmentStartDate,
        CustomerPayslipStorageService.StoredPayslip storedPayslip,
        CustomerIdentityCardStorageService.StoredIdentityCard storedIdentityCardFront,
        CustomerIdentityCardStorageService.StoredIdentityCard storedIdentityCardBack
    ) {
        if (existing == null) {
            return true;
        }
        if (storedPayslip != null || storedIdentityCardFront != null || storedIdentityCardBack != null) {
            return true;
        }
        if (!Objects.equals(fullName, normalizeNullable(existing.fullName()))) {
            return true;
        }
        if (!Objects.equals(phone, normalizeNullable(existing.phone()))) {
            return true;
        }
        if (!Objects.equals(identityNumber, normalizeIdentityNumber(existing.identityNumber()))) {
            return true;
        }
        if (!Objects.equals(dateOfBirth, existing.dateOfBirth())) {
            return true;
        }
        if (!sameMoney(monthlyIncome, existing.monthlyIncome())) {
            return true;
        }
        if (!Objects.equals(employmentStatus, normalizeEmploymentStatus(existing.employmentStatus()))) {
            return true;
        }
        return !Objects.equals(employmentStartDate, existing.employmentStartDate());
    }

    private boolean shouldClearVerifiedMonthlyIncome(
        CustomerProfile existing,
        BigDecimal monthlyIncome,
        CustomerPayslipStorageService.StoredPayslip storedPayslip
    ) {
        if (existing == null) {
            return true;
        }
        if (storedPayslip != null) {
            return true;
        }
        return !sameMoney(monthlyIncome, existing.monthlyIncome());
    }

    private boolean sameMoney(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == null && right == null;
        }
        return left.compareTo(right) == 0;
    }
}
