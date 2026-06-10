package com.loanapproval.dss.repayment;

import com.loanapproval.dss.contract.LoanContract;
import com.loanapproval.dss.contract.LoanInstallmentService;
import com.loanapproval.dss.contract.LoanContractService;
import com.loanapproval.dss.contract.LoanContractStatus;
import com.loanapproval.dss.loan.LoanPurpose;
import com.loanapproval.dss.loan.LoanRecord;
import com.loanapproval.dss.loan.LoanRepository;
import com.loanapproval.dss.loan.LoanStatus;
import com.loanapproval.dss.loan.LoanStatusHistoryService;
import com.loanapproval.dss.loan.LoanType;
import com.loanapproval.dss.notification.NotificationService;
import com.loanapproval.dss.profile.CustomerProfileRepository;
import com.loanapproval.dss.repayment.dto.RepaymentHistoryResponse;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    private LoanInstallmentService loanInstallmentService;

    @Mock
    private RepaymentScheduleService repaymentScheduleService;

    @Mock
    private LoanDelinquencyRepository loanDelinquencyRepository;

    @Mock
    private LoanDelinquencyService loanDelinquencyService;

    @Mock
    private LoanStatusHistoryService loanStatusHistoryService;

    @Mock
    private NotificationService notificationService;

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
        when(loanRepository.findById(100L)).thenReturn(Optional.of(loan(LoanStatus.ACTIVE)));
        when(loanContractService.findByLoanRequestId(100L)).thenReturn(null);

        ResponseStatusException exception = Assertions.assertThrows(
                ResponseStatusException.class,
                () -> repaymentService.createByStaff(100L, 1L, BigDecimal.valueOf(1_000_000), Instant.now(), null));

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(repaymentRepository, never()).create(any(), any(), any(), any(), any(), any(), any(), anyInt(), any());
    }

    @Test
    void shouldApplyModerateRewardForEarlyFullInstallment() {
        when(loanRepository.findById(100L)).thenReturn(Optional.of(loan(LoanStatus.ACTIVE)));
        when(loanContractService.findByLoanRequestId(100L)).thenReturn(contract());
        when(customerProfileRepository.findPaymentRatingByUserId(1L)).thenReturn(Optional.of(10));
        when(repaymentScheduleService.snapshot(any(), any(), any(Long.class), any(LocalDate.class))).thenReturn(new LoanRepaymentSnapshot(
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
        when(customerProfileRepository.adjustPaymentRating(1L, 4)).thenReturn(Optional.of(14));

        var response = repaymentService.createByStaff(
                100L,
                1L,
                BigDecimal.valueOf(4_500_000),
                Instant.now(),
                null);

        Assertions.assertEquals(BigDecimal.valueOf(4_500_000), response.repayment().amountDue());
        Assertions.assertEquals(RepaymentStatus.EARLY, response.repayment().repaymentStatus());
        Assertions.assertEquals(4, response.repayment().ratingDelta());
        Assertions.assertEquals(14, response.currentRating());
    }

    @Test
    void shouldApplySmallRewardForOnTimeFullInstallment() {
        Instant paidAt = Instant.parse("2026-05-20T03:00:00Z");
        when(loanRepository.findById(100L)).thenReturn(Optional.of(loan(LoanStatus.ACTIVE)));
        when(loanContractService.findByLoanRequestId(100L)).thenReturn(contract());
        when(customerProfileRepository.findPaymentRatingByUserId(1L)).thenReturn(Optional.of(10));
        when(repaymentScheduleService.snapshot(any(), any(), any(Long.class), any(LocalDate.class))).thenReturn(new LoanRepaymentSnapshot(
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
        when(customerProfileRepository.adjustPaymentRating(1L, 2)).thenReturn(Optional.of(12));

        var response = repaymentService.createByStaff(
                100L,
                1L,
                BigDecimal.valueOf(4_500_000),
                paidAt,
                null);

        Assertions.assertEquals(RepaymentStatus.ON_TIME, response.repayment().repaymentStatus());
        Assertions.assertEquals(2, response.repayment().ratingDelta());
        Assertions.assertEquals(12, response.currentRating());
    }

    @Test
    void shouldUseEffectivePaidAtInsteadOfClientPaidAt() {
        Instant simulatedPaidAt = Instant.parse("2026-05-21T03:00:00Z");
        LoanDelinquencyRecord delinquencyRecord = delinquencyRecord(LocalDate.of(2026, 5, 20), -6);
        when(loanRepository.findById(100L)).thenReturn(Optional.of(loan(LoanStatus.ACTIVE)));
        when(loanContractService.findByLoanRequestId(100L)).thenReturn(contract());
        when(customerProfileRepository.findPaymentRatingByUserId(1L)).thenReturn(Optional.of(10));
        when(repaymentScheduleService.snapshot(any(), any(), any(Long.class), any(LocalDate.class))).thenReturn(new LoanRepaymentSnapshot(
                100L,
                BigDecimal.valueOf(54_000_000),
                BigDecimal.valueOf(9_000_000),
                BigDecimal.valueOf(45_000_000),
                BigDecimal.valueOf(4_500_000),
                BigDecimal.valueOf(4_500_000),
                3,
                LocalDate.of(2026, 5, 20),
                false,
                true,
                1));
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
        when(customerProfileRepository.adjustPaymentRating(1L, 0)).thenReturn(Optional.of(10));
        when(loanDelinquencyRepository.findLatestByLoanAndDueDate(100L, LocalDate.of(2026, 5, 20)))
                .thenReturn(Optional.of(delinquencyRecord));

        var response = repaymentService.createByStaff(
                100L,
                1L,
                BigDecimal.valueOf(4_500_000),
                simulatedPaidAt,
                "demo");

        Assertions.assertEquals(simulatedPaidAt, response.repayment().paidAt());
        Assertions.assertEquals(RepaymentStatus.LATE, response.repayment().repaymentStatus());
        Assertions.assertEquals(-6, response.repayment().ratingDelta());
        verify(loanDelinquencyService, times(2)).assessLoan(eq(100L), eq(LocalDate.of(2026, 5, 21)));
    }

    @Test
    void shouldAllowPartialPrepaymentAboveCurrentInstallmentWhenOutstandingRemains() {
        when(loanRepository.findById(100L)).thenReturn(Optional.of(loan(LoanStatus.ACTIVE)));
        when(loanContractService.findByLoanRequestId(100L)).thenReturn(contract());
        when(customerProfileRepository.findPaymentRatingByUserId(1L)).thenReturn(Optional.of(10));
        when(repaymentScheduleService.snapshot(any(), any(), any(Long.class), any(LocalDate.class))).thenReturn(new LoanRepaymentSnapshot(
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
                        2L,
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
        when(customerProfileRepository.adjustPaymentRating(1L, 4)).thenReturn(Optional.of(14));

        var response = repaymentService.createByStaff(
                100L,
                1L,
                BigDecimal.valueOf(5_000_000),
                Instant.now(),
                null);

        Assertions.assertEquals(BigDecimal.valueOf(4_500_000), response.repayment().amountDue());
        Assertions.assertEquals(BigDecimal.valueOf(5_000_000), response.repayment().amountPaid());
        Assertions.assertEquals(RepaymentStatus.EARLY, response.repayment().repaymentStatus());
        Assertions.assertEquals(14, response.currentRating());
    }

    @Test
    void shouldRejectPaymentGreaterThanOutstandingAmount() {
        when(loanRepository.findById(100L)).thenReturn(Optional.of(loan(LoanStatus.ACTIVE)));
        when(loanContractService.findByLoanRequestId(100L)).thenReturn(contract());
        when(customerProfileRepository.findPaymentRatingByUserId(1L)).thenReturn(Optional.of(10));
        when(repaymentScheduleService.snapshot(any(), any(), any(Long.class), any(LocalDate.class))).thenReturn(new LoanRepaymentSnapshot(
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

    @Test
    void shouldExposeLatePenaltyInRepaymentHistoryWhenStoredRepaymentDeltaIsZero() {
        RepaymentRecord record = new RepaymentRecord(
                1L,
                100L,
                1L,
                BigDecimal.valueOf(4_500_000),
                BigDecimal.valueOf(4_500_000),
                LocalDate.of(2026, 5, 20),
                Instant.parse("2026-05-21T03:00:00Z"),
                RepaymentStatus.LATE,
                0,
                "bill",
                Instant.now());
        LoanDelinquencyRecord delinquencyRecord = delinquencyRecord(LocalDate.of(2026, 5, 20), -6);

        when(customerProfileRepository.findPaymentRatingByUserId(1L)).thenReturn(Optional.of(4));
        when(repaymentRepository.findByCustomerId(1L)).thenReturn(java.util.List.of(record));
        when(loanDelinquencyRepository.findLatestByLoanAndDueDate(100L, LocalDate.of(2026, 5, 20)))
                .thenReturn(Optional.of(delinquencyRecord));

        RepaymentHistoryResponse history = repaymentService.listMine(1L);

        Assertions.assertEquals(1, history.items().size());
        Assertions.assertEquals(-6, history.items().get(0).ratingDelta());
    }

    @Test
    void shouldNotifyCustomerWhenRepaymentFullyClosesLoan() {
        Instant paidAt = Instant.parse("2026-05-20T03:00:00Z");
        when(loanRepository.findById(100L)).thenReturn(Optional.of(loan(LoanStatus.ACTIVE)));
        when(loanContractService.findByLoanRequestId(100L)).thenReturn(contract());
        when(customerProfileRepository.findPaymentRatingByUserId(1L)).thenReturn(Optional.of(10));
        when(repaymentScheduleService.snapshot(any(), any(), any(Long.class), any(LocalDate.class)))
                .thenReturn(
                        new LoanRepaymentSnapshot(
                                100L,
                                BigDecimal.valueOf(4_500_000),
                                BigDecimal.valueOf(4_500_000),
                                BigDecimal.valueOf(4_500_000),
                                BigDecimal.valueOf(4_500_000),
                                BigDecimal.valueOf(4_500_000),
                                12,
                                LocalDate.of(2026, 5, 20),
                                false),
                        new LoanRepaymentSnapshot(
                                100L,
                                BigDecimal.valueOf(4_500_000),
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                BigDecimal.valueOf(4_500_000),
                                12,
                                LocalDate.of(2026, 5, 20),
                                true));
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
        when(customerProfileRepository.adjustPaymentRating(1L, 2)).thenReturn(Optional.of(12));

        var response = repaymentService.createByStaff(
                100L,
                1L,
                BigDecimal.valueOf(4_500_000),
                paidAt,
                "tất toán",
                8L);

        Assertions.assertEquals(RepaymentStatus.ON_TIME, response.repayment().repaymentStatus());
        verify(loanRepository).updateStatus(100L, LoanStatus.CLOSED);
        verify(notificationService).notifyCustomerLoanClosed(100L, 1L, 8L);
    }

    private LoanDelinquencyRecord delinquencyRecord(LocalDate dueDate, int totalRatingDelta) {
        Instant now = Instant.now();
        return new LoanDelinquencyRecord(
                10L,
                100L,
                1L,
                1,
                dueDate,
                BigDecimal.valueOf(4_500_000),
                BigDecimal.ZERO,
                1,
                1,
                totalRatingDelta,
                BigDecimal.ZERO,
                LoanDelinquencyStatus.CURED,
                now,
                now,
                now,
                now,
                now);
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
