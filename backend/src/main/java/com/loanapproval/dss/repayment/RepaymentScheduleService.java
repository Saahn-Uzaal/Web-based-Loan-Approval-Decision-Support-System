package com.loanapproval.dss.repayment;

import com.loanapproval.dss.contract.LoanContract;
import com.loanapproval.dss.contract.LoanInstallment;
import com.loanapproval.dss.contract.LoanInstallmentService;
import com.loanapproval.dss.loan.LoanRecord;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RepaymentScheduleService {

    private final LoanInstallmentService loanInstallmentService;

    public RepaymentScheduleService(LoanInstallmentService loanInstallmentService) {
        this.loanInstallmentService = loanInstallmentService;
    }

    public LoanRepaymentSnapshot snapshot(LoanRecord loan, LoanContract contract, Long customerId) {
        return snapshot(loan, contract, customerId, LocalDate.now());
    }

    public LoanRepaymentSnapshot snapshot(LoanRecord loan, LoanContract contract, Long customerId, LocalDate today) {
        loanInstallmentService.ensureSchedule(contract);
        return snapshotFromInstallments(loan, contract, loanInstallmentService.listByLoanRequestId(loan.id()), today);
    }

    public LoanRepaymentSnapshot snapshot(LoanRecord loan, LoanContract contract, BigDecimal totalPaidBefore) {
        return snapshot(loan, contract, totalPaidBefore, LocalDate.now());
    }

    public LoanRepaymentSnapshot snapshot(
            LoanRecord loan,
            LoanContract contract,
            BigDecimal totalPaidBefore,
            LocalDate today) {
        loanInstallmentService.ensureSchedule(contract);
        return snapshotFromInstallments(loan, contract, loanInstallmentService.listByLoanRequestId(loan.id()), today);
    }

    private LoanRepaymentSnapshot snapshotFromInstallments(
            LoanRecord loan,
            LoanContract contract,
            List<LoanInstallment> installments,
            LocalDate today) {
        BigDecimal totalRepayable = installments.stream()
                .map(LoanInstallment::scheduledAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPaid = installments.stream()
                .map(LoanInstallment::paidAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outstandingAmount = totalRepayable.subtract(totalPaid).max(BigDecimal.ZERO);
        LoanInstallment currentInstallment = installments.stream()
                .filter(installment -> installment.remainingAmount().compareTo(BigDecimal.ZERO) > 0)
                .findFirst()
                .orElse(null);

        if (currentInstallment == null) {
            return new LoanRepaymentSnapshot(
                    loan.id(),
                    totalRepayable,
                    totalPaid,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    contract.termMonths(),
                    contract.finalPaymentDate(),
                    true);
        }

        LocalDate effectiveToday = today != null ? today : LocalDate.now();
        boolean overdue = currentInstallment.dueDate() != null
                && currentInstallment.remainingAmount().compareTo(BigDecimal.ZERO) > 0
                && currentInstallment.dueDate().isBefore(effectiveToday);
        long overdueDays = overdue ? ChronoUnit.DAYS.between(currentInstallment.dueDate(), effectiveToday) : 0;

        return new LoanRepaymentSnapshot(
                loan.id(),
                totalRepayable,
                totalPaid,
                outstandingAmount,
                currentInstallment.remainingAmount(),
                currentInstallment.scheduledAmount(),
                currentInstallment.installmentNumber(),
                currentInstallment.dueDate(),
                false,
                overdue,
                overdueDays);
    }
}
