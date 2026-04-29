package com.loanapproval.dss.repayment;

import com.loanapproval.dss.contract.LoanContract;
import com.loanapproval.dss.loan.LoanRecord;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;

@Service
public class RepaymentScheduleService {

    private final RepaymentRepository repaymentRepository;

    public RepaymentScheduleService(RepaymentRepository repaymentRepository) {
        this.repaymentRepository = repaymentRepository;
    }

    public LoanRepaymentSnapshot snapshot(LoanRecord loan, LoanContract contract, Long customerId) {
        BigDecimal totalPaid = repaymentRepository.sumAmountPaidByLoanRequestAndCustomer(loan.id(), customerId);
        return snapshot(loan, contract, totalPaid);
    }

    public LoanRepaymentSnapshot snapshot(LoanRecord loan, LoanContract contract, BigDecimal totalPaidBefore) {
        BigDecimal totalRepayable = scaleMoney(contract.principalAmount().add(contract.totalInterest()));
        BigDecimal totalPaid = scaleMoney(totalPaidBefore).max(BigDecimal.ZERO).min(totalRepayable);
        BigDecimal outstandingAmount = totalRepayable.subtract(totalPaid).max(BigDecimal.ZERO);
        int termMonths = resolveTermMonths(loan, contract);

        if (outstandingAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return new LoanRepaymentSnapshot(
                    loan.id(),
                    totalRepayable,
                    totalPaid,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    termMonths,
                    null,
                    true);
        }

        BigDecimal scheduledInstallmentAmount = resolveScheduledInstallmentAmount(loan, contract);
        int completedInstallments = totalPaid.divideToIntegralValue(scheduledInstallmentAmount).intValue();
        completedInstallments = Math.max(0, Math.min(completedInstallments, Math.max(termMonths - 1, 0)));

        BigDecimal fullyPaidInstallmentsAmount =
                scheduledInstallmentAmount.multiply(BigDecimal.valueOf(completedInstallments));
        BigDecimal paidTowardCurrentInstallment = totalPaid.subtract(fullyPaidInstallmentsAmount).max(BigDecimal.ZERO);
        int installmentNumber = Math.min(termMonths, completedInstallments + 1);
        boolean finalInstallment = installmentNumber >= termMonths;
        BigDecimal installmentBaseAmount = finalInstallment
                ? outstandingAmount.add(paidTowardCurrentInstallment)
                : scheduledInstallmentAmount;
        BigDecimal currentAmountDue = installmentBaseAmount
                .subtract(paidTowardCurrentInstallment)
                .max(BigDecimal.ZERO)
                .min(outstandingAmount);
        LocalDate dueDate = resolveDueDate(contract, installmentNumber);
        LocalDate today = LocalDate.now();
        boolean overdue = dueDate != null
                && currentAmountDue.compareTo(BigDecimal.ZERO) > 0
                && dueDate.isBefore(today);
        long overdueDays = overdue ? ChronoUnit.DAYS.between(dueDate, today) : 0;

        return new LoanRepaymentSnapshot(
                loan.id(),
                totalRepayable,
                totalPaid,
                outstandingAmount,
                currentAmountDue,
                installmentBaseAmount,
                installmentNumber,
                dueDate,
                false,
                overdue,
                overdueDays);
    }

    private BigDecimal resolveScheduledInstallmentAmount(LoanRecord loan, LoanContract contract) {
        if (contract.monthlyPayment() != null && contract.monthlyPayment().compareTo(BigDecimal.ZERO) > 0) {
            return scaleMoney(contract.monthlyPayment()).max(BigDecimal.ONE);
        }
        return loan.amount()
                .divide(BigDecimal.valueOf(resolveTermMonths(loan, contract)), 0, RoundingMode.HALF_UP)
                .max(BigDecimal.ONE);
    }

    private int resolveTermMonths(LoanRecord loan, LoanContract contract) {
        Integer termMonths = contract.termMonths() != null ? contract.termMonths() : loan.termMonths();
        return termMonths != null && termMonths > 0 ? termMonths : 1;
    }

    private LocalDate resolveDueDate(LoanContract contract, int installmentNumber) {
        LocalDate firstPaymentDate = contract.firstPaymentDate();
        if (firstPaymentDate == null) {
            LocalDate startDate = contract.startDate() != null ? contract.startDate() : LocalDate.now();
            firstPaymentDate = startDate.plusMonths(1);
        }

        int safeInstallmentNumber = Math.max(installmentNumber, 1);
        if (contract.termMonths() != null
                && safeInstallmentNumber >= contract.termMonths()
                && contract.finalPaymentDate() != null) {
            return contract.finalPaymentDate();
        }

        return firstPaymentDate.plusMonths(safeInstallmentNumber - 1L);
    }

    private BigDecimal scaleMoney(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value.setScale(0, RoundingMode.HALF_UP);
    }
}
