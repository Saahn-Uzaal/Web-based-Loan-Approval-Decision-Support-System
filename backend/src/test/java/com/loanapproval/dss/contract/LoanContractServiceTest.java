package com.loanapproval.dss.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loanapproval.dss.compliance.ComplianceAuditService;
import com.loanapproval.dss.loan.LoanEligibilityService;
import com.loanapproval.dss.loan.LoanPurpose;
import com.loanapproval.dss.loan.LoanRecord;
import com.loanapproval.dss.loan.LoanRepository;
import com.loanapproval.dss.loan.LoanStatus;
import com.loanapproval.dss.loan.LoanType;
import com.loanapproval.dss.notification.NotificationService;
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
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class LoanContractServiceTest {

    @Mock
    private LoanContractRepository loanContractRepository;

    @Mock
    private LoanRepository loanRepository;

    @Mock
    private ComplianceAuditService complianceAuditService;

    @Mock
    private LoanEligibilityService loanEligibilityService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private LoanInstallmentService loanInstallmentService;

    @InjectMocks
    private LoanContractService loanContractService;

    @Test
    void shouldNotExposeCancelledContractForWithdrawnLoan() {
        Long customerId = 1L;
        Long loanRequestId = 100L;
        when(loanRepository.findOwnedById(loanRequestId, customerId))
                .thenReturn(Optional.of(loan(loanRequestId, customerId, LoanStatus.WITHDRAWN)));
        when(loanContractRepository.findByLoanRequestIdAndCustomerId(loanRequestId, customerId))
                .thenReturn(Optional.of(contract(loanRequestId, customerId, LoanContractStatus.CANCELLED)));

        assertThatThrownBy(() -> loanContractService.getMine(customerId, loanRequestId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
        verify(loanInstallmentService, never()).ensureSchedule(any());
    }

    private LoanRecord loan(Long loanRequestId, Long customerId, LoanStatus status) {
        Instant now = Instant.now();
        return new LoanRecord(
                loanRequestId,
                customerId,
                LoanType.UNSECURED,
                BigDecimal.valueOf(100_000_000),
                24,
                LoanPurpose.PERSONAL,
                null,
                null,
                status,
                null,
                BigDecimal.valueOf(100_000_000),
                BigDecimal.valueOf(100_000_000),
                24,
                BigDecimal.valueOf(0.12),
                BigDecimal.valueOf(4_800_000),
                "TEST_POLICY",
                null,
                now,
                now);
    }

    private LoanContract contract(Long loanRequestId, Long customerId, LoanContractStatus status) {
        Instant now = Instant.now();
        return new LoanContract(
                99L,
                loanRequestId,
                customerId,
                BigDecimal.valueOf(100_000_000),
                BigDecimal.valueOf(0.12),
                24,
                LocalDate.now(),
                LocalDate.now().plusMonths(24),
                LocalDate.now().plusMonths(1),
                "15",
                LocalDate.now().plusMonths(24),
                BigDecimal.valueOf(4_800_000),
                BigDecimal.valueOf(15_200_000),
                status,
                now,
                now);
    }
}
