package com.loanapproval.dss.repayment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.loanapproval.dss.contract.LoanContract;
import com.loanapproval.dss.contract.LoanContractStatus;
import com.loanapproval.dss.contract.LoanInstallment;
import com.loanapproval.dss.contract.LoanInstallmentService;
import com.loanapproval.dss.contract.LoanInstallmentStatus;
import com.loanapproval.dss.loan.LoanPurpose;
import com.loanapproval.dss.loan.LoanRecord;
import com.loanapproval.dss.loan.LoanStatus;
import com.loanapproval.dss.loan.LoanType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RepaymentScheduleServiceTest {

    @Mock
    private LoanInstallmentService loanInstallmentService;

    @Test
    void shouldUseFirstAndFinalPaymentDatesFromContract() {
        RepaymentScheduleService service = new RepaymentScheduleService(loanInstallmentService);
        when(loanInstallmentService.listByLoanRequestId(100L)).thenReturn(installments(false));

        LoanRepaymentSnapshot snapshot = service.snapshot(loan(), contract(), 1L);

        assertThat(snapshot.installmentNumber()).isEqualTo(3);
        assertThat(snapshot.dueDate()).isEqualTo(LocalDate.of(2026, 7, 15));
        assertThat(snapshot.overdue()).isFalse();
        assertThat(snapshot.overdueDays()).isZero();
    }

    @Test
    void shouldMarkCurrentInstallmentOverdueWhenDueDatePassedAndUnpaid() {
        RepaymentScheduleService service = new RepaymentScheduleService(loanInstallmentService);
        when(loanInstallmentService.listByLoanRequestId(100L)).thenReturn(installments(true));

        LoanRepaymentSnapshot snapshot = service.snapshot(loan(), overdueContract(), 1L);

        assertThat(snapshot.overdue()).isTrue();
        assertThat(snapshot.overdueDays()).isGreaterThan(0);
    }

    @Test
    void shouldIncludeScheduledFeesInTotalRepayable() {
        RepaymentScheduleService service = new RepaymentScheduleService(loanInstallmentService);
        when(loanInstallmentService.listByLoanRequestId(100L)).thenReturn(List.of(
                installment(
                        1L,
                        1,
                        LocalDate.of(2026, 5, 15),
                        BigDecimal.valueOf(4_500_000),
                        BigDecimal.valueOf(4_500_000),
                        BigDecimal.ZERO,
                        Instant.now()),
                installment(
                        2L,
                        2,
                        LocalDate.of(2026, 6, 15),
                        BigDecimal.valueOf(4_500_000),
                        BigDecimal.ZERO,
                        BigDecimal.valueOf(250_000),
                        null)));

        LoanRepaymentSnapshot snapshot = service.snapshot(
                loan(),
                contract(),
                1L,
                LocalDate.of(2026, 5, 20));

        assertThat(snapshot.totalRepayable()).isEqualByComparingTo(BigDecimal.valueOf(9_250_000));
        assertThat(snapshot.currentAmountDue()).isEqualByComparingTo(BigDecimal.valueOf(4_750_000));
    }

    private List<LoanInstallment> installments(boolean overdue) {
        Instant now = Instant.now();
        LocalDate dueDate = overdue ? LocalDate.now().minusDays(10) : LocalDate.of(2026, 7, 15);
        return List.of(
                installment(1L, 1, LocalDate.of(2026, 5, 15), BigDecimal.valueOf(4_500_000), BigDecimal.valueOf(4_500_000), now),
                installment(2L, 2, LocalDate.of(2026, 6, 15), BigDecimal.valueOf(4_500_000), BigDecimal.valueOf(4_500_000), now),
                installment(3L, 3, dueDate, BigDecimal.valueOf(4_500_000), BigDecimal.ZERO, null));
    }

    private LoanInstallment installment(
            Long id,
            int installmentNumber,
            LocalDate dueDate,
            BigDecimal scheduledAmount,
            BigDecimal paidAmount,
            Instant paidAt) {
        return installment(id, installmentNumber, dueDate, scheduledAmount, paidAmount, BigDecimal.ZERO, paidAt);
    }

    private LoanInstallment installment(
            Long id,
            int installmentNumber,
            LocalDate dueDate,
            BigDecimal scheduledAmount,
            BigDecimal paidAmount,
            BigDecimal scheduledFee,
            Instant paidAt) {
        BigDecimal scheduledPrincipal = BigDecimal.valueOf(4_000_000);
        BigDecimal scheduledInterest = scheduledAmount.subtract(scheduledPrincipal);
        return new LoanInstallment(
                id,
                99L,
                100L,
                1L,
                installmentNumber,
                dueDate,
                BigDecimal.valueOf(50_000_000),
                scheduledPrincipal,
                scheduledInterest,
                BigDecimal.ZERO.setScale(2),
                scheduledFee,
                scheduledAmount,
                paidAmount.min(scheduledPrincipal),
                paidAmount.compareTo(scheduledPrincipal) > 0
                        ? paidAmount.subtract(scheduledPrincipal)
                        : BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2),
                paidAmount,
                paidAt,
                paidAmount.compareTo(scheduledAmount) >= 0 ? LoanInstallmentStatus.PAID : LoanInstallmentStatus.PENDING,
                now(),
                now());
    }

    private Instant now() {
        return Instant.now();
    }

    private LoanRecord loan() {
        Instant now = Instant.now();
        return new LoanRecord(
                100L,
                1L,
                LoanType.UNSECURED,
                BigDecimal.valueOf(50_000_000),
                12,
                LoanPurpose.PERSONAL,
                null,
                null,
                LoanStatus.ACTIVE,
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
                LocalDate.of(2026, 4, 15),
                LocalDate.of(2027, 3, 15),
                LocalDate.of(2026, 5, 15),
                "15",
                LocalDate.of(2027, 4, 15),
                BigDecimal.valueOf(4_500_000),
                BigDecimal.valueOf(4_000_000),
                LoanContractStatus.ACTIVE,
                now,
                now);
    }

    private LoanContract overdueContract() {
        Instant now = Instant.now();
        LocalDate firstPaymentDate = LocalDate.now().minusDays(10);
        return new LoanContract(
                99L,
                100L,
                1L,
                BigDecimal.valueOf(50_000_000),
                BigDecimal.valueOf(0.12),
                12,
                LocalDate.now().minusMonths(1),
                LocalDate.now().plusMonths(11),
                firstPaymentDate,
                "15",
                firstPaymentDate.plusMonths(11),
                BigDecimal.valueOf(4_500_000),
                BigDecimal.valueOf(4_000_000),
                LoanContractStatus.ACTIVE,
                now,
                now);
    }
}
