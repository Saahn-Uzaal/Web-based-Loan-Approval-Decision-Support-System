package com.loanapproval.dss.contract;

import com.loanapproval.dss.repayment.RepaymentRecord;
import com.loanapproval.dss.repayment.RepaymentRepository;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoanInstallmentService {

    private static final MathContext MATH_CONTEXT = new MathContext(18, RoundingMode.HALF_UP);

    private final LoanInstallmentRepository loanInstallmentRepository;
    private final RepaymentRepository repaymentRepository;

    public LoanInstallmentService(
            LoanInstallmentRepository loanInstallmentRepository,
            RepaymentRepository repaymentRepository) {
        this.loanInstallmentRepository = loanInstallmentRepository;
        this.repaymentRepository = repaymentRepository;
    }

    @Transactional
    public void ensureSchedule(LoanContract contract) {
        if (contract == null || contract.id() == null) {
            return;
        }
        if (loanInstallmentRepository.countByContractId(contract.id()) > 0) {
            return;
        }
        for (LoanInstallment installment : buildSchedule(contract)) {
            loanInstallmentRepository.create(installment);
        }
        rebuildLedger(contract);
    }

    public List<LoanInstallment> listByLoanRequestId(Long loanRequestId) {
        return loanInstallmentRepository.findByLoanRequestId(loanRequestId);
    }

    public List<LoanInstallment> listByContractId(Long loanContractId) {
        return loanInstallmentRepository.findByContractId(loanContractId);
    }

    @Transactional
    public void rebuildLedger(LoanContract contract) {
        if (contract == null) {
            return;
        }
        if (loanInstallmentRepository.countByContractId(contract.id()) == 0) {
            ensureSchedule(contract);
            return;
        }

        List<LoanInstallment> installments = loanInstallmentRepository.findByContractId(contract.id());
        List<RepaymentRecord> repayments = repaymentRepository.findByLoanRequestAndCustomerOrderByPaidAtAsc(
                contract.loanRequestId(),
                contract.customerId());
        loanInstallmentRepository.resetLedger(contract.loanRequestId());

        List<MutableInstallment> mutableInstallments = installments.stream()
                .map(MutableInstallment::from)
                .toList();
        for (RepaymentRecord repayment : repayments) {
            allocateAcrossInstallments(mutableInstallments, repayment.amountPaid(), repayment.paidAt());
        }
        for (MutableInstallment installment : mutableInstallments) {
            loanInstallmentRepository.updateLedgerState(
                    installment.id,
                    installment.paidPrincipal,
                    installment.paidInterest,
                    installment.paidFee,
                    installment.totalPaid(),
                    installment.lastPaidAt,
                    installment.status());
        }
    }

    private List<LoanInstallment> buildSchedule(LoanContract contract) {
        int termMonths = contract.termMonths() != null && contract.termMonths() > 0 ? contract.termMonths() : 1;
        BigDecimal principal = money(contract.principalAmount());
        BigDecimal monthlyRate = contract.annualInterestRate() != null
                ? contract.annualInterestRate().divide(BigDecimal.valueOf(12), 12, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal scheduledPayment = money(contract.monthlyPayment());
        BigDecimal remainingPrincipal = principal;
        LocalDate firstPaymentDate = contract.firstPaymentDate() != null
                ? contract.firstPaymentDate()
                : contract.startDate().plusMonths(1);
        List<LoanInstallment> installments = new ArrayList<>();

        for (int installmentNumber = 1; installmentNumber <= termMonths; installmentNumber++) {
            BigDecimal openingPrincipal = remainingPrincipal;
            BigDecimal interestDue;
            BigDecimal principalDue;
            if (monthlyRate.compareTo(BigDecimal.ZERO) == 0) {
                interestDue = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                principalDue = principal.divide(
                                BigDecimal.valueOf(termMonths),
                                2,
                                RoundingMode.HALF_UP)
                        .min(remainingPrincipal);
            } else {
                interestDue = remainingPrincipal.multiply(monthlyRate, MATH_CONTEXT).setScale(2, RoundingMode.HALF_UP);
                principalDue = scheduledPayment.subtract(interestDue).setScale(2, RoundingMode.HALF_UP);
                if (principalDue.compareTo(BigDecimal.ZERO) < 0) {
                    principalDue = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                }
                principalDue = principalDue.min(remainingPrincipal);
            }

            if (installmentNumber == termMonths) {
                principalDue = remainingPrincipal;
                scheduledPayment = principalDue.add(interestDue).setScale(2, RoundingMode.HALF_UP);
            }

            LocalDate dueDate = installmentNumber == termMonths && contract.finalPaymentDate() != null
                    ? contract.finalPaymentDate()
                    : firstPaymentDate.plusMonths(installmentNumber - 1L);

            installments.add(new LoanInstallment(
                    null,
                    contract.id(),
                    contract.loanRequestId(),
                    contract.customerId(),
                    installmentNumber,
                    dueDate,
                    openingPrincipal,
                    principalDue,
                    interestDue,
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    principalDue.add(interestDue).setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    null,
                    LoanInstallmentStatus.PENDING,
                    null,
                    null));
            remainingPrincipal = remainingPrincipal.subtract(principalDue).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        }

        return installments;
    }

    private void allocateAcrossInstallments(
            List<MutableInstallment> installments,
            BigDecimal rawAmountPaid,
            Instant paidAt) {
        BigDecimal remaining = money(rawAmountPaid);
        for (MutableInstallment installment : installments) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                return;
            }
            if (installment.remainingAmount().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal payFee = remaining.min(installment.remainingFee());
            installment.paidFee = installment.paidFee.add(payFee);
            remaining = remaining.subtract(payFee);

            BigDecimal payInterest = remaining.min(installment.remainingInterest());
            installment.paidInterest = installment.paidInterest.add(payInterest);
            remaining = remaining.subtract(payInterest);

            BigDecimal payPrincipal = remaining.min(installment.remainingPrincipal());
            installment.paidPrincipal = installment.paidPrincipal.add(payPrincipal);
            remaining = remaining.subtract(payPrincipal);

            if (payFee.compareTo(BigDecimal.ZERO) > 0
                    || payInterest.compareTo(BigDecimal.ZERO) > 0
                    || payPrincipal.compareTo(BigDecimal.ZERO) > 0) {
                installment.lastPaidAt = paidAt;
            }
        }
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static final class MutableInstallment {
        private final Long id;
        private final BigDecimal scheduledPrincipal;
        private final BigDecimal scheduledInterest;
        private final BigDecimal scheduledFee;
        private BigDecimal paidPrincipal;
        private BigDecimal paidInterest;
        private BigDecimal paidFee;
        private Instant lastPaidAt;

        private MutableInstallment(
                Long id,
                BigDecimal scheduledPrincipal,
                BigDecimal scheduledInterest,
                BigDecimal scheduledFee,
                BigDecimal paidPrincipal,
                BigDecimal paidInterest,
                BigDecimal paidFee,
                Instant lastPaidAt) {
            this.id = id;
            this.scheduledPrincipal = scheduledPrincipal;
            this.scheduledInterest = scheduledInterest;
            this.scheduledFee = scheduledFee;
            this.paidPrincipal = paidPrincipal;
            this.paidInterest = paidInterest;
            this.paidFee = paidFee;
            this.lastPaidAt = lastPaidAt;
        }

        private static MutableInstallment from(LoanInstallment installment) {
            return new MutableInstallment(
                    installment.id(),
                    installment.scheduledPrincipal(),
                    installment.scheduledInterest(),
                    installment.scheduledFee(),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    null);
        }

        private BigDecimal totalPaid() {
            return paidPrincipal.add(paidInterest).add(paidFee).setScale(2, RoundingMode.HALF_UP);
        }

        private BigDecimal remainingAmount() {
            return scheduledPrincipal.add(scheduledInterest).add(scheduledFee).subtract(totalPaid()).max(BigDecimal.ZERO);
        }

        private BigDecimal remainingPrincipal() {
            return scheduledPrincipal.subtract(paidPrincipal).max(BigDecimal.ZERO);
        }

        private BigDecimal remainingInterest() {
            return scheduledInterest.subtract(paidInterest).max(BigDecimal.ZERO);
        }

        private BigDecimal remainingFee() {
            return scheduledFee.subtract(paidFee).max(BigDecimal.ZERO);
        }

        private LoanInstallmentStatus status() {
            if (totalPaid().compareTo(BigDecimal.ZERO) <= 0) {
                return LoanInstallmentStatus.PENDING;
            }
            if (remainingAmount().compareTo(BigDecimal.ZERO) <= 0) {
                return LoanInstallmentStatus.PAID;
            }
            return LoanInstallmentStatus.PARTIALLY_PAID;
        }
    }
}
