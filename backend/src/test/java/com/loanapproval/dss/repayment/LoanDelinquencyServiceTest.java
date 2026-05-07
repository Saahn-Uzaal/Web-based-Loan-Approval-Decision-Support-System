package com.loanapproval.dss.repayment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loanapproval.dss.contract.LoanContract;
import com.loanapproval.dss.contract.LoanContractStatus;
import com.loanapproval.dss.loan.LoanStatusHistoryService;
import com.loanapproval.dss.loan.LoanPurpose;
import com.loanapproval.dss.loan.LoanRecord;
import com.loanapproval.dss.loan.LoanRepository;
import com.loanapproval.dss.loan.LoanStatus;
import com.loanapproval.dss.loan.LoanType;
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

@ExtendWith(MockitoExtension.class)
class LoanDelinquencyServiceTest {

    @Mock
    private LoanDelinquencyRepository loanDelinquencyRepository;

    @Mock
    private RepaymentScheduleService repaymentScheduleService;

    @Mock
    private CustomerProfileRepository customerProfileRepository;

    @Mock
    private LoanRepository loanRepository;

    @Mock
    private LoanStatusHistoryService loanStatusHistoryService;

    @InjectMocks
    private LoanDelinquencyService loanDelinquencyService;

    @Test
    void shouldOpenOverdueLoanAndApplyFirstDpdMilestone() {
        LocalDate assessmentDate = LocalDate.of(2026, 5, 21);
        LoanRecord loan = loan(LoanStatus.ACTIVE);
        LoanContract contract = contract();
        LoanRepaymentSnapshot snapshot = snapshot(true, 1, BigDecimal.valueOf(4_500_000));
        LoanDelinquencyRecord delinquency = delinquency(0);

        when(loanDelinquencyRepository.findActiveCandidates()).thenReturn(List.of(new LoanDelinquencyCandidate(loan, contract)));
        when(repaymentScheduleService.snapshot(loan, contract, 1L, assessmentDate)).thenReturn(snapshot);
        when(loanDelinquencyRepository.upsertOpen(any(), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(delinquency);
        when(customerProfileRepository.adjustPaymentRating(1L, -6)).thenReturn(Optional.of(4));

        LoanDelinquencyRunSummary summary = loanDelinquencyService.assessAll(assessmentDate);

        assertThat(summary.scannedLoans()).isEqualTo(1);
        assertThat(summary.openedOrUpdated()).isEqualTo(1);
        assertThat(summary.ratingAdjustments()).isEqualTo(1);
        verify(loanRepository).updateStatus(100L, LoanStatus.OVERDUE);
        verify(loanDelinquencyRepository).updateMilestoneAndDelta(10L, 1, -6);
    }

    @Test
    void shouldApplyVeryLargePenaltyForRepeatedLateMilestones() {
        LocalDate assessmentDate = LocalDate.of(2026, 6, 19);
        LoanRecord loan = loan(LoanStatus.OVERDUE);
        LoanContract contract = contract();
        LoanRepaymentSnapshot snapshot = snapshot(true, 30, BigDecimal.valueOf(4_500_000));
        LoanDelinquencyRecord delinquency = delinquency(1);

        when(loanDelinquencyRepository.findActiveCandidates())
                .thenReturn(java.util.List.of(new LoanDelinquencyCandidate(loan, contract)));
        when(repaymentScheduleService.snapshot(loan, contract, 1L, assessmentDate)).thenReturn(snapshot);
        when(loanDelinquencyRepository.upsertOpen(any(), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(delinquency);
        when(customerProfileRepository.adjustPaymentRating(1L, -20)).thenReturn(Optional.of(-10));

        LoanDelinquencyRunSummary summary = loanDelinquencyService.assessAll(assessmentDate);

        assertThat(summary.ratingAdjustments()).isEqualTo(1);
        verify(loanDelinquencyRepository).updateMilestoneAndDelta(10L, 30, -20);
    }

    @Test
    void shouldApplyExtremePenaltyForBadDebtMilestone() {
        LocalDate assessmentDate = LocalDate.of(2026, 8, 23);
        LoanRecord loan = loan(LoanStatus.OVERDUE);
        LoanContract contract = contract();
        LoanRepaymentSnapshot snapshot = snapshot(true, 95, BigDecimal.valueOf(4_500_000));
        LoanDelinquencyRecord delinquency = delinquency(60);

        when(loanDelinquencyRepository.findActiveCandidates())
                .thenReturn(java.util.List.of(new LoanDelinquencyCandidate(loan, contract)));
        when(repaymentScheduleService.snapshot(loan, contract, 1L, assessmentDate)).thenReturn(snapshot);
        when(loanDelinquencyRepository.upsertOpen(any(), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(delinquency);
        when(customerProfileRepository.adjustPaymentRating(1L, -30)).thenReturn(Optional.of(-40));

        LoanDelinquencyRunSummary summary = loanDelinquencyService.assessAll(assessmentDate);

        assertThat(summary.ratingAdjustments()).isEqualTo(1);
        verify(loanDelinquencyRepository).updateMilestoneAndDelta(10L, 90, -30);
    }

    @Test
    void shouldNotApplySameMilestoneTwice() {
        LocalDate assessmentDate = LocalDate.of(2026, 5, 27);
        LoanRecord loan = loan(LoanStatus.OVERDUE);
        LoanContract contract = contract();
        LoanRepaymentSnapshot snapshot = snapshot(true, 7, BigDecimal.valueOf(4_500_000));
        LoanDelinquencyRecord delinquency = delinquency(7);

        when(loanDelinquencyRepository.findActiveCandidates()).thenReturn(List.of(new LoanDelinquencyCandidate(loan, contract)));
        when(repaymentScheduleService.snapshot(loan, contract, 1L, assessmentDate)).thenReturn(snapshot);
        when(loanDelinquencyRepository.upsertOpen(any(), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(delinquency);

        LoanDelinquencyRunSummary summary = loanDelinquencyService.assessAll(assessmentDate);

        assertThat(summary.ratingAdjustments()).isZero();
        verify(customerProfileRepository, never()).adjustPaymentRating(any(), anyInt());
        verify(loanDelinquencyRepository, never()).updateMilestoneAndDelta(any(), anyInt(), anyInt());
    }

    @Test
    void shouldCureOverdueLoanOnlyAfterCurrentInstallmentIsPaidEnough() {
        LocalDate assessmentDate = LocalDate.of(2026, 5, 21);
        LoanRecord loan = loan(LoanStatus.OVERDUE);
        LoanContract contract = contract();
        LoanRepaymentSnapshot snapshot = snapshot(false, 0, BigDecimal.ZERO);

        when(loanDelinquencyRepository.findCandidateByLoanRequestId(100L))
                .thenReturn(Optional.of(new LoanDelinquencyCandidate(loan, contract)));
        when(repaymentScheduleService.snapshot(loan, contract, 1L, assessmentDate)).thenReturn(snapshot);
        when(loanDelinquencyRepository.markAllOpenCured(100L)).thenReturn(1);

        LoanDelinquencyRunSummary summary = loanDelinquencyService.assessLoan(100L, assessmentDate);

        assertThat(summary.cured()).isEqualTo(1);
        verify(loanRepository).updateStatus(100L, LoanStatus.ACTIVE);
        verify(loanDelinquencyRepository, never()).upsertOpen(any(), any(), any(), any(), any(), any(), anyInt());
    }

    private LoanRepaymentSnapshot snapshot(boolean overdue, long overdueDays, BigDecimal currentAmountDue) {
        return new LoanRepaymentSnapshot(
                100L,
                BigDecimal.valueOf(54_000_000),
                overdue ? BigDecimal.ZERO : BigDecimal.valueOf(4_500_000),
                overdue ? BigDecimal.valueOf(54_000_000) : BigDecimal.valueOf(49_500_000),
                currentAmountDue,
                BigDecimal.valueOf(4_500_000),
                overdue ? 1 : 2,
                overdue ? LocalDate.of(2026, 5, 20) : LocalDate.of(2026, 6, 20),
                false,
                overdue,
                overdueDays);
    }

    private LoanDelinquencyRecord delinquency(int highestMilestone) {
        Instant now = Instant.now();
        return new LoanDelinquencyRecord(
                10L,
                100L,
                1L,
                1,
                LocalDate.of(2026, 5, 20),
                BigDecimal.valueOf(4_500_000),
                BigDecimal.valueOf(4_500_000),
                1,
                highestMilestone,
                0,
                LoanDelinquencyStatus.OPEN,
                now,
                now,
                null,
                now,
                now);
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
