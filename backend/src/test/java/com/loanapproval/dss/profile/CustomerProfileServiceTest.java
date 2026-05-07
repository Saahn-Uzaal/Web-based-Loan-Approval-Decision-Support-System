package com.loanapproval.dss.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @InjectMocks
    private CustomerProfileService customerProfileService;

    @Test
    void shouldClearVerifiedIncomeWhenCustomerUpdatesProfile() {
        Long userId = 42L;
        CustomerProfile existing = profile(userId, BigDecimal.valueOf(25_000_000), BigDecimal.valueOf(22_000_000));
        CustomerProfile savedAfterInvalidation = profile(userId, BigDecimal.valueOf(30_000_000), null);
        CustomerProfileRequest request = new CustomerProfileRequest(
                "Nguyễn Minh An",
                "0901234567",
                LocalDate.of(1994, 5, 12),
                BigDecimal.valueOf(30_000_000),
                "EMPLOYED",
                LocalDate.of(2020, 1, 1));

        when(customerProfileRepository.findByUserId(userId))
                .thenReturn(Optional.of(existing), Optional.of(savedAfterInvalidation));
        when(customerDebtService.recalculateAndSyncDti(userId)).thenReturn(BigDecimal.valueOf(10));

        CustomerProfileResponse response = customerProfileService.upsert(userId, request, null);

        assertThat(response.verifiedMonthlyIncome()).isNull();
        verify(customerProfileRepository).clearVerifiedMonthlyIncome(userId);
        verify(customerInformationVerificationService).markPending(userId);
    }

    @Test
    void shouldNormalizeEmploymentStatusToEnumValue() {
        Long userId = 43L;
        CustomerProfile existing = profile(userId, BigDecimal.valueOf(25_000_000), BigDecimal.valueOf(22_000_000));
        CustomerProfile saved = new CustomerProfile(
            existing.userId(),
            existing.fullName(),
            existing.phone(),
            existing.dateOfBirth(),
            existing.monthlyIncome(),
            null,
            existing.debtToIncomeRatio(),
            "EMPLOYED",
            existing.employmentStartDate(),
            existing.creditHistoryScore(),
            existing.paymentRating(),
            existing.payslipOriginalFilename(),
            existing.payslipStorageName(),
            existing.payslipContentType(),
            existing.payslipFileSize(),
            existing.payslipUploadedAt()
        );
        CustomerProfileRequest request = new CustomerProfileRequest(
            "Nguyễn Minh An",
            "0901234567",
            LocalDate.of(1994, 5, 12),
            BigDecimal.valueOf(30_000_000),
            "Nhân viên kỹ thuật",
            LocalDate.of(2020, 1, 1)
        );

        when(customerProfileRepository.findByUserId(userId))
            .thenReturn(Optional.of(existing), Optional.of(saved));
        when(customerDebtService.recalculateAndSyncDti(userId)).thenReturn(BigDecimal.valueOf(10));

        CustomerProfileResponse response = customerProfileService.upsert(userId, request, null);

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
            existing.dateOfBirth(),
            existing.monthlyIncome(),
            null,
            existing.debtToIncomeRatio(),
            null,
            null,
            existing.creditHistoryScore(),
            existing.paymentRating(),
            existing.payslipOriginalFilename(),
            existing.payslipStorageName(),
            existing.payslipContentType(),
            existing.payslipFileSize(),
            existing.payslipUploadedAt()
        );
        CustomerProfileRequest request = new CustomerProfileRequest(
            "Nguyễn Minh An",
            "0901234567",
            LocalDate.of(1994, 5, 12),
            BigDecimal.valueOf(30_000_000),
            null,
            null
        );

        when(customerProfileRepository.findByUserId(userId))
            .thenReturn(Optional.of(existing), Optional.of(saved));
        when(customerDebtService.recalculateAndSyncDti(userId)).thenReturn(BigDecimal.valueOf(10));

        CustomerProfileResponse response = customerProfileService.upsert(userId, request, null);

        assertThat(response.employmentStatus()).isNull();
        assertThat(response.employmentStartDate()).isNull();
    }

    private CustomerProfile profile(Long userId, BigDecimal monthlyIncome, BigDecimal verifiedMonthlyIncome) {
        Instant now = Instant.now();
        return new CustomerProfile(
                userId,
                "Nguyễn Minh An",
                "0901234567",
                LocalDate.of(1994, 5, 12),
                monthlyIncome,
                verifiedMonthlyIncome,
                BigDecimal.valueOf(10),
                "EMPLOYED",
                LocalDate.of(2020, 1, 1),
                720,
                0,
                "payslip.pdf",
                "stored-payslip.pdf",
                "application/pdf",
                1024L,
                now);
    }
}
