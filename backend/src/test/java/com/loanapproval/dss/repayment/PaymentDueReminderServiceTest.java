package com.loanapproval.dss.repayment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loanapproval.dss.contract.LoanContract;
import com.loanapproval.dss.contract.LoanContractStatus;
import com.loanapproval.dss.loan.LoanPurpose;
import com.loanapproval.dss.loan.LoanRecord;
import com.loanapproval.dss.loan.LoanStatus;
import com.loanapproval.dss.loan.LoanType;
import com.loanapproval.dss.notification.NotificationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentDueReminderServiceTest {

    @Mock
    private LoanDelinquencyRepository loanDelinquencyRepository;

    @Mock
    private RepaymentScheduleService repaymentScheduleService;

    @Mock
    private PaymentDueReminderRepository paymentDueReminderRepository;

    @Mock
    private NotificationService notificationService;

    private PaymentDueReminderService paymentDueReminderService;

    @BeforeEach
    void setUp() {
        paymentDueReminderService = new PaymentDueReminderService(
                loanDelinquencyRepository,
                repaymentScheduleService,
                paymentDueReminderRepository,
                notificationService,
                3);
    }

    @Test
    void shouldNotifyCustomerWhenCurrentInstallmentIsDueWithinWindow() {
        LocalDate today = LocalDate.of(2026, 5, 17);
        LoanRecord loan = loan(LoanStatus.ACTIVE);
        LoanContract contract = contract();
        LoanRepaymentSnapshot snapshot = snapshot(LocalDate.of(2026, 5, 20), false);

        when(loanDelinquencyRepository.findActiveCandidates()).thenReturn(List.of(new LoanDelinquencyCandidate(loan, contract)));
        when(repaymentScheduleService.snapshot(loan, contract, 1L, today)).thenReturn(snapshot);
        when(paymentDueReminderRepository.createIfMissing(
                eq(100L),
                eq(1L),
                eq(1),
                eq(LocalDate.of(2026, 5, 20)),
                eq(BigDecimal.valueOf(4_500_000)))).thenReturn(true);

        PaymentDueReminderRunSummary summary = paymentDueReminderService.sendDueSoonReminders(today);

        assertThat(summary.scannedLoans()).isEqualTo(1);
        assertThat(summary.remindersSent()).isEqualTo(1);
        verify(notificationService).notifyCustomerPaymentDueSoon(
                100L,
                1L,
                1,
                LocalDate.of(2026, 5, 20),
                BigDecimal.valueOf(4_500_000),
                BigDecimal.valueOf(54_000_000));
    }

    @Test
    void shouldNotNotifyAgainForSameInstallmentDueDate() {
        LocalDate today = LocalDate.of(2026, 5, 17);
        LoanRecord loan = loan(LoanStatus.ACTIVE);
        LoanContract contract = contract();

        when(loanDelinquencyRepository.findActiveCandidates()).thenReturn(List.of(new LoanDelinquencyCandidate(loan, contract)));
        when(repaymentScheduleService.snapshot(loan, contract, 1L, today))
                .thenReturn(snapshot(LocalDate.of(2026, 5, 20), false));
        when(paymentDueReminderRepository.createIfMissing(any(), any(), any(), any(), any())).thenReturn(false);

        PaymentDueReminderRunSummary summary = paymentDueReminderService.sendDueSoonReminders(today);

        assertThat(summary.remindersSent()).isZero();
        verify(notificationService, never()).notifyCustomerPaymentDueSoon(any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldSkipOverdueInstallmentBecauseDelinquencyFlowHandlesIt() {
        LocalDate today = LocalDate.of(2026, 5, 21);
        LoanRecord loan = loan(LoanStatus.OVERDUE);
        LoanContract contract = contract();

        when(loanDelinquencyRepository.findActiveCandidates()).thenReturn(List.of(new LoanDelinquencyCandidate(loan, contract)));
        when(repaymentScheduleService.snapshot(loan, contract, 1L, today))
                .thenReturn(snapshot(LocalDate.of(2026, 5, 20), true));

        PaymentDueReminderRunSummary summary = paymentDueReminderService.sendDueSoonReminders(today);

        assertThat(summary.remindersSent()).isZero();
        verify(paymentDueReminderRepository, never()).createIfMissing(any(), any(), any(), any(), any());
        verify(notificationService, never()).notifyCustomerPaymentDueSoon(any(), any(), any(), any(), any(), any());
    }

    private LoanRepaymentSnapshot snapshot(LocalDate dueDate, boolean overdue) {
        return new LoanRepaymentSnapshot(
                100L,
                BigDecimal.valueOf(54_000_000),
                BigDecimal.ZERO,
                BigDecimal.valueOf(54_000_000),
                BigDecimal.valueOf(4_500_000),
                BigDecimal.valueOf(4_500_000),
                1,
                dueDate,
                false,
                overdue,
                overdue ? 1 : 0);
    }

    private LoanRecord loan(LoanStatus status) {
        Instant now = Instant.now();
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
                now,
                now);
    }

    private LoanContract contract() {
        Instant now = Instant.now();
        return new LoanContract(
                99L,
                100L,
                1L,
                BigDecimal.valueOf(50_000_000),
                BigDecimal.valueOf(0.12),
                12,
                LocalDate.of(2026, 4, 20),
                LocalDate.of(2027, 4, 20),
                LocalDate.of(2026, 5, 20),
                "20",
                LocalDate.of(2027, 4, 20),
                BigDecimal.valueOf(4_500_000),
                BigDecimal.valueOf(4_000_000),
                LoanContractStatus.ACTIVE,
                now,
                now);
    }
}
