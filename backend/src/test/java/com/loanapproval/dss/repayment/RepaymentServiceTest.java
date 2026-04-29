package com.loanapproval.dss.repayment;

import com.loanapproval.dss.contract.LoanContract;
import com.loanapproval.dss.contract.LoanContractService;
import com.loanapproval.dss.contract.LoanContractStatus;
import com.loanapproval.dss.loan.LoanPurpose;
import com.loanapproval.dss.loan.LoanRecord;
import com.loanapproval.dss.loan.LoanRepository;
import com.loanapproval.dss.loan.LoanStatus;
import com.loanapproval.dss.loan.LoanType;
import com.loanapproval.dss.profile.CustomerProfileRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepaymentServiceTest {

    @Mock
    private RepaymentRepository repaymentRepository;

    @Mock
    private LoanRepository loanRepository;

    @Mock
    private CustomerProfileRepository customerProfileRepository;

    @Mock
    private LoanContractService loanContractService;

    @Mock
    private RepaymentScheduleService repaymentScheduleService;

    @InjectMocks
    private RepaymentService repaymentService;

    @Test
    void shouldRejectPaymentBeforeDisbursement() {
        when(loanRepository.findById(100L)).thenReturn(Optional.of(loan(LoanStatus.APPROVED)));

        ResponseStatusException exception = Assertions.assertThrows(
                ResponseStatusException.class,
                () -> repaymentService.createByStaff(100L, 1L, BigDecimal.valueOf(1_000_000), Instant.now(), null));

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(repaymentRepository, never()).create(any(), any(), any(), any(), any(), any(), any(), anyInt(), any());
    }

    @Test
    void shouldRejectPaymentWithoutActiveContract() {
        when(loanRepository.findById(100L)).thenReturn(Optional.of(loan(LoanStatus.DISBURSED)));
        when(loanContractService.findByLoanRequestId(100L)).thenReturn(null);

        ResponseStatusException exception = Assertions.assertThrows(
                ResponseStatusException.class,
                () -> repaymentService.createByStaff(100L, 1L, BigDecimal.valueOf(1_000_000), Instant.now(), null));

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(repaymentRepository, never()).create(any(), any(), any(), any(), any(), any(), any(), anyInt(), any());
    }

    @Test
    void shouldRewardConfirmedInstallmentWhenAmountMatchesCurrentDue() {
        when(loanRepository.findById(100L)).thenReturn(Optional.of(loan(LoanStatus.ACTIVE)));
        when(loanContractService.findByLoanRequestId(100L)).thenReturn(contract());
        when(customerProfileRepository.findPaymentRatingByUserId(1L)).thenReturn(Optional.of(10));
        when(repaymentScheduleService.snapshot(any(), any(), any(Long.class))).thenReturn(new LoanRepaymentSnapshot(
                100L,
                BigDecimal.valueOf(54_000_000),
                BigDecimal.valueOf(9_000_000),
                BigDecimal.valueOf(45_000_000),
                BigDecimal.valueOf(4_500_000),
                BigDecimal.valueOf(4_500_000),
                3,
                LocalDate.now().plusDays(2),
                false));
        when(repaymentRepository.create(any(), any(), any(), any(), any(), any(), any(), anyInt(), any()))
                .thenAnswer(invocation -> new RepaymentRecord(
                        1L,
                        100L,
                        1L,
                        invocation.getArgument(2),
                        invocation.getArgument(3),
                        invocation.getArgument(4),
                        invocation.getArgument(5),
                        invocation.getArgument(6),
                        invocation.getArgument(7),
                        invocation.getArgument(8),
                        Instant.now()));
        when(customerProfileRepository.adjustPaymentRating(1L, 5)).thenReturn(Optional.of(15));

        var response = repaymentService.createByStaff(
                100L,
                1L,
                BigDecimal.valueOf(4_500_000),
                Instant.now(),
                null);

        Assertions.assertEquals(BigDecimal.valueOf(4_500_000), response.repayment().amountDue());
        Assertions.assertEquals(5, response.repayment().ratingDelta());
        Assertions.assertEquals(15, response.currentRating());
    }

    @Test
    void shouldUseEffectivePaidAtInsteadOfClientPaidAt() {
        Instant simulatedPaidAt = Instant.parse("2026-05-21T03:00:00Z");
        when(loanRepository.findById(100L)).thenReturn(Optional.of(loan(LoanStatus.ACTIVE)));
        when(loanContractService.findByLoanRequestId(100L)).thenReturn(contract());
        when(customerProfileRepository.findPaymentRatingByUserId(1L)).thenReturn(Optional.of(10));
        when(repaymentScheduleService.snapshot(any(), any(), any(Long.class))).thenReturn(new LoanRepaymentSnapshot(
                100L,
                BigDecimal.valueOf(54_000_000),
                BigDecimal.valueOf(9_000_000),
                BigDecimal.valueOf(45_000_000),
                BigDecimal.valueOf(4_500_000),
                BigDecimal.valueOf(4_500_000),
                3,
                LocalDate.of(2026, 5, 20),
                false));
        when(repaymentRepository.create(any(), any(), any(), any(), any(), any(), any(), anyInt(), any()))
                .thenAnswer(invocation -> new RepaymentRecord(
                        1L,
                        100L,
                        1L,
                        invocation.getArgument(2),
                        invocation.getArgument(3),
                        invocation.getArgument(4),
                        invocation.getArgument(5),
                        invocation.getArgument(6),
                        invocation.getArgument(7),
                        invocation.getArgument(8),
                        Instant.now()));
        when(customerProfileRepository.adjustPaymentRating(1L, -8)).thenReturn(Optional.of(2));

        var response = repaymentService.createByStaff(
                100L,
                1L,
                BigDecimal.valueOf(4_500_000),
                simulatedPaidAt,
                "demo");

        Assertions.assertEquals(simulatedPaidAt, response.repayment().paidAt());
        Assertions.assertEquals(RepaymentStatus.LATE, response.repayment().repaymentStatus());
        Assertions.assertEquals(-8, response.repayment().ratingDelta());
    }

    @Test
    void shouldRejectAmbiguousPrepaymentBetweenInstallmentAndOutstanding() {
        when(loanRepository.findById(100L)).thenReturn(Optional.of(loan(LoanStatus.ACTIVE)));
        when(loanContractService.findByLoanRequestId(100L)).thenReturn(contract());
        when(customerProfileRepository.findPaymentRatingByUserId(1L)).thenReturn(Optional.of(10));
        when(repaymentScheduleService.snapshot(any(), any(), any(Long.class))).thenReturn(new LoanRepaymentSnapshot(
                100L,
                BigDecimal.valueOf(54_000_000),
                BigDecimal.valueOf(9_000_000),
                BigDecimal.valueOf(45_000_000),
                BigDecimal.valueOf(4_500_000),
                BigDecimal.valueOf(4_500_000),
                3,
                LocalDate.now().plusDays(2),
                false));

        ResponseStatusException exception = Assertions.assertThrows(
                ResponseStatusException.class,
                () -> repaymentService.createByStaff(
                        100L,
                        1L,
                        BigDecimal.valueOf(5_000_000),
                        Instant.now(),
                        null));

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(repaymentRepository, never()).create(any(), any(), any(), any(), any(), any(), any(), anyInt(), any());
    }

    @Test
    void shouldRejectPaymentGreaterThanOutstandingAmount() {
        when(loanRepository.findById(100L)).thenReturn(Optional.of(loan(LoanStatus.ACTIVE)));
        when(loanContractService.findByLoanRequestId(100L)).thenReturn(contract());
        when(customerProfileRepository.findPaymentRatingByUserId(1L)).thenReturn(Optional.of(10));
        when(repaymentScheduleService.snapshot(any(), any(), any(Long.class))).thenReturn(new LoanRepaymentSnapshot(
                100L,
                BigDecimal.valueOf(54_000_000),
                BigDecimal.valueOf(53_000_000),
                BigDecimal.valueOf(1_000_000),
                BigDecimal.valueOf(1_000_000),
                BigDecimal.valueOf(1_000_000),
                12,
                LocalDate.now().plusDays(1),
                false));

        ResponseStatusException exception = Assertions.assertThrows(
                ResponseStatusException.class,
                () -> repaymentService.createByStaff(
                        100L,
                        1L,
                        BigDecimal.valueOf(1_500_000),
                        Instant.now(),
                        null));

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(repaymentRepository, never()).create(any(), any(), any(), any(), any(), any(), any(), anyInt(), any());
    }

    private LoanRecord loan(LoanStatus status) {
        return new LoanRecord(
                100L,
                1L,
                LoanType.UNSECURED,
                BigDecimal.valueOf(50_000_000),
                12,
                LoanPurpose.PERSONAL,
                null,
                status,
                null,
                BigDecimal.valueOf(50_000_000),
                BigDecimal.valueOf(50_000_000),
                12,
                BigDecimal.valueOf(0.12),
                BigDecimal.valueOf(4_500_000),
                "TEST_POLICY",
                null,
                Instant.now(),
                Instant.now());
    }

    private LoanContract contract() {
        return new LoanContract(
                99L,
                100L,
                1L,
                BigDecimal.valueOf(50_000_000),
                BigDecimal.valueOf(0.12),
                12,
                LocalDate.now().minusMonths(2),
                LocalDate.now().plusMonths(10),
                LocalDate.now().minusMonths(1),
                "15",
                LocalDate.now().plusMonths(9),
                BigDecimal.valueOf(4_500_000),
                BigDecimal.valueOf(4_000_000),
                LoanContractStatus.ACTIVE,
                Instant.now(),
                Instant.now());
    }
}
