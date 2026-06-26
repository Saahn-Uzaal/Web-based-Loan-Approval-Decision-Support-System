package com.loanapproval.dss.debt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loanapproval.dss.debt.dto.CreateCustomerDebtRequest;
import com.loanapproval.dss.debt.dto.CustomerDebtMetricsResponse;
import com.loanapproval.dss.debt.dto.CustomerDebtResponse;
import com.loanapproval.dss.creditcheck.CreditBureauRecord;
import com.loanapproval.dss.creditcheck.CreditBureauRepository;
import com.loanapproval.dss.creditcheck.CreditBureauStatus;
import com.loanapproval.dss.profile.CustomerProfile;
import com.loanapproval.dss.profile.CustomerProfileRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class CustomerDebtServiceTest {

    @Mock
    private CustomerDebtRepository customerDebtRepository;

    @Mock
    private CustomerProfileRepository customerProfileRepository;

    @Mock
    private com.loanapproval.dss.loan.LoanRepository loanRepository;

    @Mock
    private CreditBureauRepository creditBureauRepository;

    @InjectMocks
    private CustomerDebtService customerDebtService;

    @Test
    void shouldCreateDebtAndNormalizeTextFields() {
        Long customerId = 10L;
        CreateCustomerDebtRequest request = new CreateCustomerDebtRequest(
            "  Vay   mua xe  ",
            BigDecimal.valueOf(2_500_000),
            BigDecimal.valueOf(40_000_000),
            "  Techcombank  "
        );
        CustomerDebt createdDebt = debt(
            100L,
            customerId,
            "Vay mua xe",
            DebtStatus.PENDING_VERIFICATION,
            BigDecimal.valueOf(2_500_000),
            BigDecimal.valueOf(40_000_000),
            "Techcombank"
        );
        when(customerDebtRepository.create(
            customerId,
            "Vay mua xe",
            request.monthlyPayment(),
            request.remainingBalance(),
            "Techcombank"
        )).thenReturn(createdDebt);

        CustomerDebtResponse response = customerDebtService.create(customerId, request);

        assertThat(response.id()).isEqualTo(createdDebt.id());
        assertThat(response.debtType()).isEqualTo("Vay mua xe");
        assertThat(response.lenderName()).isEqualTo("Techcombank");
        assertThat(response.status()).isEqualTo(DebtStatus.PENDING_VERIFICATION);
    }

    @Test
    void shouldReturnDebtMetricsIncludingCommittedLoans() {
        Long customerId = 20L;
        when(customerDebtRepository.findByCustomerId(customerId)).thenReturn(List.of(
            debt(1L, customerId, "Debt A", DebtStatus.PENDING_VERIFICATION, BigDecimal.valueOf(1_000_000), BigDecimal.TEN, "Bank A"),
            debt(2L, customerId, "Debt B", DebtStatus.VERIFIED, BigDecimal.valueOf(2_000_000), BigDecimal.TEN, "Bank B"),
            debt(3L, customerId, "Debt C", DebtStatus.VERIFIED, BigDecimal.valueOf(3_000_000), BigDecimal.TEN, "Bank C"),
            debt(4L, customerId, "Debt D", DebtStatus.REJECTED, BigDecimal.valueOf(4_000_000), BigDecimal.TEN, "Bank D")
        ));
        when(customerDebtRepository.sumActiveMonthlyDebt(customerId)).thenReturn(BigDecimal.valueOf(5_000_000));
        when(loanRepository.sumCommittedMonthlyPaymentByCustomerId(customerId)).thenReturn(BigDecimal.valueOf(7_500_000));
        when(customerProfileRepository.findByUserId(customerId)).thenReturn(Optional.of(profile(customerId, BigDecimal.valueOf(41.67))));
        when(creditBureauRepository.findByIdentityNumber("012345678901")).thenReturn(Optional.empty());

        CustomerDebtMetricsResponse response = customerDebtService.getMetrics(customerId);

        assertThat(response.totalDebtCount()).isEqualTo(4);
        assertThat(response.pendingVerificationCount()).isEqualTo(1);
        assertThat(response.verifiedDebtCount()).isEqualTo(2);
        assertThat(response.rejectedDebtCount()).isEqualTo(1);
        assertThat(response.verifiedMonthlyDebt()).isEqualByComparingTo("5000000");
        assertThat(response.totalMonthlyObligation()).isEqualByComparingTo("12500000");
        assertThat(response.debtToIncomeRatio()).isEqualByComparingTo("41.67");
    }

    @Test
    void shouldUseHigherRegistryExternalObligationWhenCalculatingDti() {
        Long customerId = 40L;
        when(customerDebtRepository.sumActiveMonthlyDebt(customerId)).thenReturn(BigDecimal.valueOf(2_000_000));
        when(loanRepository.sumCommittedMonthlyPaymentByCustomerId(customerId)).thenReturn(BigDecimal.valueOf(3_000_000));
        when(customerProfileRepository.findByUserId(customerId)).thenReturn(Optional.of(profile(customerId, BigDecimal.ZERO)));
        when(customerProfileRepository.findEffectiveMonthlyIncomeByUserId(customerId)).thenReturn(Optional.of(BigDecimal.valueOf(20_000_000)));
        when(creditBureauRepository.findByIdentityNumber("012345678901")).thenReturn(Optional.of(
            new CreditBureauRecord(
                "012345678901",
                "Nguyen Van A",
                CreditBureauStatus.CLEAR,
                80,
                2,
                0,
                false,
                false,
                null,
                BigDecimal.valueOf(9_000_000),
                BigDecimal.valueOf(70_000_000),
                BigDecimal.valueOf(6_000_000),
                BigDecimal.valueOf(50_000_000),
                2,
                true,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")
            )
        ));

        BigDecimal dti = customerDebtService.recalculateAndSyncDti(customerId);

        assertThat(dti).isEqualByComparingTo("45.00");
        verify(customerProfileRepository).updateDebtToIncomeRatio(customerId, BigDecimal.valueOf(45.00).setScale(2));
    }

    @Test
    void shouldRejectDeletingVerifiedDebt() {
        Long customerId = 30L;
        Long debtId = 300L;
        when(customerDebtRepository.findOwnedById(debtId, customerId)).thenReturn(Optional.of(
            debt(debtId, customerId, "Debt A", DebtStatus.VERIFIED, BigDecimal.ONE, BigDecimal.ONE, "Bank A")
        ));

        assertThatThrownBy(() -> customerDebtService.deleteOwned(customerId, debtId))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> {
                ResponseStatusException exception = (ResponseStatusException) error;
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(exception.getReason()).contains("đang chờ xác minh");
            });
        verify(customerDebtRepository, never()).deleteOwned(debtId, customerId);
    }

    private CustomerDebt debt(
        Long id,
        Long customerId,
        String debtType,
        DebtStatus status,
        BigDecimal monthlyPayment,
        BigDecimal remainingBalance,
        String lenderName
    ) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new CustomerDebt(
            id,
            customerId,
            debtType,
            monthlyPayment,
            remainingBalance,
            lenderName,
            status,
            now,
            now
        );
    }

    private CustomerProfile profile(Long userId, BigDecimal debtToIncomeRatio) {
        Instant uploadedAt = Instant.parse("2026-01-01T00:00:00Z");
        return new CustomerProfile(
            userId,
            "Nguyen Van A",
            "0900000000",
            "012345678901",
            LocalDate.of(1990, 1, 1),
            BigDecimal.valueOf(30_000_000),
            BigDecimal.valueOf(30_000_000),
            debtToIncomeRatio,
            "PERMANENT",
            LocalDate.of(2020, 1, 1),
            "1234567890",
            "Vietcombank",
            700,
            50,
            "payslip.pdf",
            "payslip.pdf",
            "application/pdf",
            1200L,
            uploadedAt,
            "front.png",
            "front.png",
            "image/png",
            100L,
            uploadedAt,
            "back.png",
            "back.png",
            "image/png",
            100L,
            uploadedAt
        );
    }
}
