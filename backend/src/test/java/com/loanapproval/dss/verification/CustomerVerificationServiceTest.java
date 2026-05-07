package com.loanapproval.dss.verification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loanapproval.dss.compliance.ComplianceAuditService;
import com.loanapproval.dss.customerinfo.CustomerInformationVerificationService;
import com.loanapproval.dss.verification.dto.UpdateCustomerVerificationRequest;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerVerificationServiceTest {

    @Mock
    private CustomerVerificationRepository customerVerificationRepository;

    @Mock
    private CustomerInformationVerificationService customerInformationVerificationService;

    @Mock
    private ComplianceAuditService complianceAuditService;

    @InjectMocks
    private CustomerVerificationService customerVerificationService;

    @Test
    void shouldSyncInformationVerificationAfterStaffUpdatesLoanVerification() {
        Long customerId = 55L;
        Long staffUserId = 8L;
        CustomerVerification current = new CustomerVerification(
            customerId,
            VerificationStatus.PENDING,
            VerificationStatus.PENDING,
            VerificationStatus.PENDING,
            VerificationStatus.PENDING,
            VerificationStatus.PENDING,
            VerificationStatus.PENDING,
            false,
            null,
            null,
            null,
            Instant.now(),
            Instant.now()
        );
        UpdateCustomerVerificationRequest request = new UpdateCustomerVerificationRequest(
            VerificationStatus.PASSED,
            VerificationStatus.PASSED,
            VerificationStatus.PASSED,
            VerificationStatus.PASSED,
            VerificationStatus.PASSED,
            VerificationStatus.PASSED,
            false,
            "checked by staff"
        );

        when(customerVerificationRepository.findByCustomerId(customerId)).thenReturn(Optional.of(current));

        customerVerificationService.upsert(customerId, staffUserId, request);

        verify(customerVerificationRepository).upsert(any(CustomerVerification.class));
        verify(customerInformationVerificationService).syncFromLoanApprovalVerification(
            eq(customerId),
            eq(staffUserId),
            any(CustomerVerification.class)
        );
    }
}
