package com.loanapproval.dss.customerinfo;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loanapproval.dss.debt.CustomerDebtRepository;
import com.loanapproval.dss.notification.NotificationService;
import com.loanapproval.dss.profile.CustomerProfile;
import com.loanapproval.dss.profile.CustomerProfileRepository;
import com.loanapproval.dss.verification.CustomerVerification;
import com.loanapproval.dss.verification.CustomerVerificationRepository;
import com.loanapproval.dss.verification.VerificationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class CustomerInformationVerificationServiceTest {

    @Mock
    private CustomerInformationVerificationRepository customerInformationVerificationRepository;

    @Mock
    private CustomerProfileRepository customerProfileRepository;

    @Mock
    private CustomerDebtRepository customerDebtRepository;

    @Mock
    private CustomerVerificationRepository customerVerificationRepository;

    @Mock
    private com.loanapproval.dss.compliance.ComplianceAuditService complianceAuditService;

    @Mock
    private NotificationService notificationService;

    @Test
    void shouldBlockRejectWhenCustomerHasNotSubmittedProfile() {
        CustomerInformationVerificationService service = service();
        Long customerId = 100L;
        Long staffUserId = 7L;
        when(customerInformationVerificationRepository.findCustomerDetailById(customerId))
            .thenReturn(Optional.of(detailWithoutProfile(customerId)));

        assertThatThrownBy(() -> service.review(
            customerId,
            staffUserId,
            new com.loanapproval.dss.customerinfo.dto.ReviewCustomerInformationRequest(
                CustomerInformationDecisionAction.REJECT,
                "Thiếu dữ liệu",
                null
            )
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> {
                ResponseStatusException exception = (ResponseStatusException) error;
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(exception.getReason()).contains("nộp đầy đủ hồ sơ");
            });

        verify(customerInformationVerificationRepository, never()).upsertDecision(
            eq(customerId),
            any(),
            any(),
            any(),
            any()
        );
    }

    @Test
    void shouldSyncFailedInformationVerificationFromLoanVerification() {
        CustomerInformationVerificationService service = service();
        Long customerId = 200L;
        Long staffUserId = 9L;
        when(customerProfileRepository.findByUserId(customerId)).thenReturn(Optional.of(submittedProfile(customerId)));

        CustomerVerification verification = new CustomerVerification(
            customerId,
            VerificationStatus.PASSED,
            VerificationStatus.PASSED,
            VerificationStatus.PASSED,
            VerificationStatus.PASSED,
            VerificationStatus.FAILED,
            VerificationStatus.PASSED,
            false,
            "manual check",
            staffUserId,
            Instant.now(),
            Instant.now(),
            Instant.now()
        );

        service.syncFromLoanApprovalVerification(customerId, staffUserId, verification);

        verify(customerInformationVerificationRepository).upsertDecision(
            eq(customerId),
            eq(VerificationStatus.FAILED),
            eq("Từ chối do KYC không đạt trong bước xác minh tổng hợp"),
            eq(staffUserId),
            any()
        );
        verify(customerInformationVerificationRepository, never()).markPending(customerId);
    }

    private CustomerInformationVerificationService service() {
        return new CustomerInformationVerificationService(
            customerInformationVerificationRepository,
            customerProfileRepository,
            customerDebtRepository,
            customerVerificationRepository,
            complianceAuditService,
            notificationService
        );
    }

    private com.loanapproval.dss.customerinfo.dto.StaffCustomerInformationDetailResponse detailWithoutProfile(Long customerId) {
        return new com.loanapproval.dss.customerinfo.dto.StaffCustomerInformationDetailResponse(
            customerId,
            "customer@example.com",
            Instant.now(),
            VerificationStatus.PENDING,
            null,
            null,
            null,
            null,
            List.of()
        );
    }

    private CustomerProfile submittedProfile(Long customerId) {
        Instant now = Instant.now();
        return new CustomerProfile(
            customerId,
            "Nguyễn Minh An",
            "0901234567",
            LocalDate.of(1994, 5, 12),
            BigDecimal.valueOf(20_000_000),
            null,
            BigDecimal.ZERO,
            "EMPLOYED",
            LocalDate.of(2020, 1, 1),
            700,
            0,
            "payslip.pdf",
            "stored.pdf",
            "application/pdf",
            1024L,
            now
        );
    }
}
