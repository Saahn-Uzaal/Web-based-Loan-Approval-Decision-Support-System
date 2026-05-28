package com.loanapproval.dss.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.loanapproval.dss.creditcheck.CustomerCreditCheckService;
import com.loanapproval.dss.customerinfo.CustomerInformationVerificationService;
import com.loanapproval.dss.debt.CustomerDebtService;
import com.loanapproval.dss.profile.dto.CustomerProfileRequest;
import com.loanapproval.dss.profile.dto.CustomerProfileResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class CustomerProfileServiceTest {

    @Mock
    private CustomerProfileRepository customerProfileRepository;

    @Mock
    private CustomerDebtService customerDebtService;

    @Mock
    private CustomerInformationVerificationService customerInformationVerificationService;

    @Mock
    private CustomerPayslipStorageService customerPayslipStorageService;

    @Mock
    private CustomerIdentityCardStorageService customerIdentityCardStorageService;

    @Mock
    private CustomerCreditCheckService customerCreditCheckService;

    @InjectMocks
    private CustomerProfileService customerProfileService;

    @Test
    void shouldClearVerifiedIncomeWhenCustomerUpdatesMonthlyIncome() {
        Long userId = 42L;
        CustomerProfile existing = profile(userId, BigDecimal.valueOf(25_000_000), BigDecimal.valueOf(22_000_000));
        CustomerProfile savedAfterInvalidation = profile(userId, BigDecimal.valueOf(30_000_000), null);
        CustomerProfileRequest request = new CustomerProfileRequest(
                "Nguyễn Minh An",
                "0901234567",
                "012345678901",
                LocalDate.of(1994, 5, 12),
                BigDecimal.valueOf(30_000_000),
                "EMPLOYED",
                LocalDate.of(2020, 1, 1),
                null,
                null);

        when(customerProfileRepository.findByUserId(userId))
                .thenReturn(Optional.of(existing), Optional.of(savedAfterInvalidation));
        when(customerDebtService.recalculateAndSyncDti(userId)).thenReturn(BigDecimal.valueOf(10));
        when(customerCreditCheckService.refreshForCustomer(eq(userId), any(CustomerProfile.class))).thenReturn(null);

        CustomerProfileResponse response = customerProfileService.upsert(userId, request, CustomerProfileFiles.empty());

        assertThat(response.verifiedMonthlyIncome()).isNull();
        verify(customerProfileRepository).clearVerifiedMonthlyIncome(userId);
        verify(customerInformationVerificationService).markPending(userId);
    }

    @Test
    void shouldKeepVerifiedIncomeWhenCustomerUpdatesPhoneNumber() {
        Long userId = 47L;
        CustomerProfile existing = profile(userId, BigDecimal.valueOf(25_000_000), BigDecimal.valueOf(22_000_000));
        CustomerProfile saved = new CustomerProfile(
                existing.userId(),
                existing.fullName(),
                "0911222333",
                existing.identityNumber(),
                existing.dateOfBirth(),
                existing.monthlyIncome(),
                existing.verifiedMonthlyIncome(),
                existing.debtToIncomeRatio(),
                existing.employmentStatus(),
                existing.employmentStartDate(),
                existing.bankAccountNumber(),
                existing.bankName(),
                existing.creditHistoryScore(),
                existing.paymentRating(),
                existing.payslipOriginalFilename(),
                existing.payslipStorageName(),
                existing.payslipContentType(),
                existing.payslipFileSize(),
                existing.payslipUploadedAt(),
                existing.identityCardFrontOriginalFilename(),
                existing.identityCardFrontStorageName(),
                existing.identityCardFrontContentType(),
                existing.identityCardFrontFileSize(),
                existing.identityCardFrontUploadedAt(),
                existing.identityCardBackOriginalFilename(),
                existing.identityCardBackStorageName(),
                existing.identityCardBackContentType(),
                existing.identityCardBackFileSize(),
                existing.identityCardBackUploadedAt());
        CustomerProfileRequest request = new CustomerProfileRequest(
                existing.fullName(),
                "0911222333",
                existing.identityNumber(),
                existing.dateOfBirth(),
                existing.monthlyIncome(),
                existing.employmentStatus(),
                existing.employmentStartDate(),
                existing.bankAccountNumber(),
                existing.bankName());

        when(customerProfileRepository.findByUserId(userId))
                .thenReturn(Optional.of(existing), Optional.of(saved));
        when(customerDebtService.recalculateAndSyncDti(userId)).thenReturn(existing.debtToIncomeRatio());
        when(customerCreditCheckService.refreshForCustomer(eq(userId), any(CustomerProfile.class))).thenReturn(null);

        CustomerProfileResponse response = customerProfileService.upsert(userId, request, CustomerProfileFiles.empty());

        assertThat(response.verifiedMonthlyIncome()).isEqualByComparingTo(existing.verifiedMonthlyIncome());
        verify(customerProfileRepository, never()).clearVerifiedMonthlyIncome(userId);
        verify(customerInformationVerificationService).markPending(userId, existing.verifiedMonthlyIncome());
    }

    @Test
    void shouldClearVerifiedIncomeWhenCustomerUploadsNewPayslip() {
        Long userId = 48L;
        Instant uploadedAt = Instant.now();
        CustomerProfile existing = profile(userId, BigDecimal.valueOf(25_000_000), BigDecimal.valueOf(22_000_000));
        CustomerPayslipStorageService.StoredPayslip storedPayslip = new CustomerPayslipStorageService.StoredPayslip(
                "new-payslip.pdf",
                "stored-new-payslip.pdf",
                "application/pdf",
                2048L,
                uploadedAt);
        CustomerProfile savedAfterInvalidation = new CustomerProfile(
                existing.userId(),
                existing.fullName(),
                existing.phone(),
                existing.identityNumber(),
                existing.dateOfBirth(),
                existing.monthlyIncome(),
                null,
                existing.debtToIncomeRatio(),
                existing.employmentStatus(),
                existing.employmentStartDate(),
                existing.bankAccountNumber(),
                existing.bankName(),
                existing.creditHistoryScore(),
                existing.paymentRating(),
                storedPayslip.originalFileName(),
                storedPayslip.storageName(),
                storedPayslip.contentType(),
                storedPayslip.fileSize(),
                storedPayslip.uploadedAt(),
                existing.identityCardFrontOriginalFilename(),
                existing.identityCardFrontStorageName(),
                existing.identityCardFrontContentType(),
                existing.identityCardFrontFileSize(),
                existing.identityCardFrontUploadedAt(),
                existing.identityCardBackOriginalFilename(),
                existing.identityCardBackStorageName(),
                existing.identityCardBackContentType(),
                existing.identityCardBackFileSize(),
                existing.identityCardBackUploadedAt());
        MultipartFile payslip = org.mockito.Mockito.mock(MultipartFile.class);
        CustomerProfileRequest request = new CustomerProfileRequest(
                existing.fullName(),
                existing.phone(),
                existing.identityNumber(),
                existing.dateOfBirth(),
                existing.monthlyIncome(),
                existing.employmentStatus(),
                existing.employmentStartDate(),
                existing.bankAccountNumber(),
                existing.bankName());

        when(customerProfileRepository.findByUserId(userId))
                .thenReturn(Optional.of(existing), Optional.of(savedAfterInvalidation));
        when(payslip.isEmpty()).thenReturn(false);
        when(customerPayslipStorageService.store(userId, payslip, existing.payslipStorageName())).thenReturn(storedPayslip);
        when(customerDebtService.recalculateAndSyncDti(userId)).thenReturn(existing.debtToIncomeRatio());
        when(customerCreditCheckService.refreshForCustomer(eq(userId), any(CustomerProfile.class))).thenReturn(null);

        CustomerProfileResponse response = customerProfileService.upsert(
                userId,
                request,
                new CustomerProfileFiles(payslip, null, null));

        assertThat(response.verifiedMonthlyIncome()).isNull();
        verify(customerProfileRepository).clearVerifiedMonthlyIncome(userId);
        verify(customerInformationVerificationService).markPending(userId);
    }

    @Test
    void shouldKeepVerifiedIncomeWhenOnlyDisbursementAccountChanges() {
        Long userId = 46L;
        CustomerProfile existing = profile(userId, BigDecimal.valueOf(25_000_000), BigDecimal.valueOf(22_000_000));
        CustomerProfile saved = new CustomerProfile(
                existing.userId(),
                existing.fullName(),
                existing.phone(),
                existing.identityNumber(),
                existing.dateOfBirth(),
                existing.monthlyIncome(),
                existing.verifiedMonthlyIncome(),
                existing.debtToIncomeRatio(),
                existing.employmentStatus(),
                existing.employmentStartDate(),
                "19071533252015",
                "Techcombank",
                existing.creditHistoryScore(),
                existing.paymentRating(),
                existing.payslipOriginalFilename(),
                existing.payslipStorageName(),
                existing.payslipContentType(),
                existing.payslipFileSize(),
                existing.payslipUploadedAt(),
                existing.identityCardFrontOriginalFilename(),
                existing.identityCardFrontStorageName(),
                existing.identityCardFrontContentType(),
                existing.identityCardFrontFileSize(),
                existing.identityCardFrontUploadedAt(),
                existing.identityCardBackOriginalFilename(),
                existing.identityCardBackStorageName(),
                existing.identityCardBackContentType(),
                existing.identityCardBackFileSize(),
                existing.identityCardBackUploadedAt());
        CustomerProfileRequest request = new CustomerProfileRequest(
                existing.fullName(),
                existing.phone(),
                existing.identityNumber(),
                existing.dateOfBirth(),
                existing.monthlyIncome(),
                existing.employmentStatus(),
                existing.employmentStartDate(),
                "19071533252015",
                "Techcombank");

        when(customerProfileRepository.findByUserId(userId))
                .thenReturn(Optional.of(existing), Optional.of(saved));
        when(customerDebtService.recalculateAndSyncDti(userId)).thenReturn(existing.debtToIncomeRatio());
        when(customerCreditCheckService.refreshForCustomer(eq(userId), any(CustomerProfile.class))).thenReturn(null);

        CustomerProfileResponse response = customerProfileService.upsert(userId, request, CustomerProfileFiles.empty());

        assertThat(response.verifiedMonthlyIncome()).isEqualByComparingTo(existing.verifiedMonthlyIncome());
        verify(customerProfileRepository, never()).clearVerifiedMonthlyIncome(userId);
        verify(customerInformationVerificationService, never()).markPending(userId);
    }

    @Test
    void shouldNormalizeEmploymentStatusToEnumValue() {
        Long userId = 43L;
        CustomerProfile existing = profile(userId, BigDecimal.valueOf(25_000_000), BigDecimal.valueOf(22_000_000));
        CustomerProfile saved = new CustomerProfile(
                existing.userId(),
                existing.fullName(),
                existing.phone(),
                existing.identityNumber(),
                existing.dateOfBirth(),
                existing.monthlyIncome(),
                null,
                existing.debtToIncomeRatio(),
                "EMPLOYED",
                existing.employmentStartDate(),
                existing.bankAccountNumber(),
                existing.bankName(),
                existing.creditHistoryScore(),
                existing.paymentRating(),
                existing.payslipOriginalFilename(),
                existing.payslipStorageName(),
                existing.payslipContentType(),
                existing.payslipFileSize(),
                existing.payslipUploadedAt(),
                existing.identityCardFrontOriginalFilename(),
                existing.identityCardFrontStorageName(),
                existing.identityCardFrontContentType(),
                existing.identityCardFrontFileSize(),
                existing.identityCardFrontUploadedAt(),
                existing.identityCardBackOriginalFilename(),
                existing.identityCardBackStorageName(),
                existing.identityCardBackContentType(),
                existing.identityCardBackFileSize(),
                existing.identityCardBackUploadedAt());
        CustomerProfileRequest request = new CustomerProfileRequest(
                "Nguyễn Minh An",
                "0901234567",
                "012345678901",
                LocalDate.of(1994, 5, 12),
                BigDecimal.valueOf(30_000_000),
                "Nhân viên kỹ thuật",
                LocalDate.of(2020, 1, 1),
                null,
                null);

        when(customerProfileRepository.findByUserId(userId))
                .thenReturn(Optional.of(existing), Optional.of(saved));
        when(customerDebtService.recalculateAndSyncDti(userId)).thenReturn(BigDecimal.valueOf(10));
        when(customerCreditCheckService.refreshForCustomer(eq(userId), any(CustomerProfile.class))).thenReturn(null);

        CustomerProfileResponse response = customerProfileService.upsert(userId, request, CustomerProfileFiles.empty());

        assertThat(response.employmentStatus()).isEqualTo("EMPLOYED");
    }

    @Test
    void shouldAllowCustomerToClearEmploymentFields() {
        Long userId = 44L;
        CustomerProfile existing = profile(userId, BigDecimal.valueOf(25_000_000), BigDecimal.valueOf(22_000_000));
        CustomerProfile saved = new CustomerProfile(
                existing.userId(),
                existing.fullName(),
                existing.phone(),
                existing.identityNumber(),
                existing.dateOfBirth(),
                existing.monthlyIncome(),
                null,
                existing.debtToIncomeRatio(),
                null,
                null,
                existing.bankAccountNumber(),
                existing.bankName(),
                existing.creditHistoryScore(),
                existing.paymentRating(),
                existing.payslipOriginalFilename(),
                existing.payslipStorageName(),
                existing.payslipContentType(),
                existing.payslipFileSize(),
                existing.payslipUploadedAt(),
                existing.identityCardFrontOriginalFilename(),
                existing.identityCardFrontStorageName(),
                existing.identityCardFrontContentType(),
                existing.identityCardFrontFileSize(),
                existing.identityCardFrontUploadedAt(),
                existing.identityCardBackOriginalFilename(),
                existing.identityCardBackStorageName(),
                existing.identityCardBackContentType(),
                existing.identityCardBackFileSize(),
                existing.identityCardBackUploadedAt());
        CustomerProfileRequest request = new CustomerProfileRequest(
                "Nguyễn Minh An",
                "0901234567",
                "012345678901",
                LocalDate.of(1994, 5, 12),
                BigDecimal.valueOf(30_000_000),
                null,
                null,
                null,
                null);

        when(customerProfileRepository.findByUserId(userId))
                .thenReturn(Optional.of(existing), Optional.of(saved));
        when(customerDebtService.recalculateAndSyncDti(userId)).thenReturn(BigDecimal.valueOf(10));
        when(customerCreditCheckService.refreshForCustomer(eq(userId), any(CustomerProfile.class))).thenReturn(null);

        CustomerProfileResponse response = customerProfileService.upsert(userId, request, CustomerProfileFiles.empty());

        assertThat(response.employmentStatus()).isNull();
        assertThat(response.employmentStartDate()).isNull();
    }

    @Test
    void shouldRequireBothBankAccountNumberAndBankName() {
        Long userId = 45L;
        CustomerProfile existing = profile(userId, BigDecimal.valueOf(25_000_000), BigDecimal.valueOf(22_000_000));
        CustomerProfileRequest request = new CustomerProfileRequest(
                "Nguyễn Minh An",
                "0901234567",
                "012345678901",
                LocalDate.of(1994, 5, 12),
                BigDecimal.valueOf(30_000_000),
                "EMPLOYED",
                LocalDate.of(2020, 1, 1),
                "19036866889922",
                null);

        when(customerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> customerProfileService.upsert(userId, request, CustomerProfileFiles.empty()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> {
                    ResponseStatusException exception = (ResponseStatusException) error;
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getReason()).contains("số tài khoản");
                });

        verify(customerProfileRepository, never()).upsert(any(CustomerProfile.class));
    }

    private CustomerProfile profile(Long userId, BigDecimal monthlyIncome, BigDecimal verifiedMonthlyIncome) {
        Instant now = Instant.now();
        return new CustomerProfile(
                userId,
                "Nguyễn Minh An",
                "0901234567",
                "012345678901",
                LocalDate.of(1994, 5, 12),
                monthlyIncome,
                verifiedMonthlyIncome,
                BigDecimal.valueOf(10),
                "EMPLOYED",
                LocalDate.of(2020, 1, 1),
                "19036866889922",
                "Vietcombank",
                720,
                0,
                "payslip.pdf",
                "stored-payslip.pdf",
                "application/pdf",
                1024L,
                now,
                "id-front.jpg",
                "stored-front.jpg",
                "image/jpeg",
                2048L,
                now,
                "id-back.jpg",
                "stored-back.jpg",
                "image/jpeg",
                2048L,
                now);
    }
}
