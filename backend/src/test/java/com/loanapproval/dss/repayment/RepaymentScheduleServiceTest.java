package com.loanapproval.dss.repayment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.loanapproval.dss.contract.LoanContract;
import com.loanapproval.dss.contract.LoanContractStatus;
import com.loanapproval.dss.loan.LoanPurpose;
import com.loanapproval.dss.loan.LoanRecord;
import com.loanapproval.dss.loan.LoanStatus;
import com.loanapproval.dss.loan.LoanType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RepaymentScheduleServiceTest {

    @Mock
    private RepaymentRepository repaymentRepository;

    @Test
    void shouldUseFirstAndFinalPaymentDatesFromContract() {
        RepaymentScheduleService service = new RepaymentScheduleService(repaymentRepository);
        when(repaymentRepository.sumAmountPaidByLoanRequestAndCustomer(100L, 1L)).thenReturn(BigDecimal.valueOf(9_000_000));

        LoanRepaymentSnapshot snapshot = service.snapshot(loan(), contract(), 1L);

        assertThat(snapshot.installmentNumber()).isEqualTo(3);
        assertThat(snapshot.dueDate()).isEqualTo(LocalDate.of(2026, 7, 15));
        assertThat(snapshot.overdue()).isFalse();
        assertThat(snapshot.overdueDays()).isZero();
    }

    @Test
    void shouldMarkCurrentInstallmentOverdueWhenDueDatePassedAndUnpaid() {
        RepaymentScheduleService service = new RepaymentScheduleService(repaymentRepository);
        when(repaymentRepository.sumAmountPaidByLoanRequestAndCustomer(100L, 1L)).thenReturn(BigDecimal.ZERO);

        LoanRepaymentSnapshot snapshot = service.snapshot(loan(), overdueContract(), 1L);

        assertThat(snapshot.overdue()).isTrue();
        assertThat(snapshot.overdueDays()).isGreaterThan(0);
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
