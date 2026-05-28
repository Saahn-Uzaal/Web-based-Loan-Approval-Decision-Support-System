package com.loanapproval.dss.contract;

import com.loanapproval.dss.repayment.RepaymentRecord;
import com.loanapproval.dss.repayment.RepaymentRepository;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoanInstallmentService {

    private static final MathContext MATH_CONTEXT = new MathContext(18, RoundingMode.HALF_UP);
    private static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault();

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
        rebuildLedger(contract, LocalDate.now());
    }

    @Transactional
    public void rebuildLedger(LoanContract contract, LocalDate asOfDate) {
        if (contract == null) {
            return;
        }
        if (loanInstallmentRepository.countByContractId(contract.id()) == 0) {
            for (LoanInstallment installment : buildSchedule(contract)) {
                loanInstallmentRepository.create(installment);
            }
        }

        LocalDate effectiveDate = asOfDate != null ? asOfDate : LocalDate.now();
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
                    installment.waivedInterest,
                    installment.paidPrincipal,
                    installment.paidInterest,
                    installment.paidFee,
                    installment.totalPaid(),
                    installment.lastPaidAt,
                    installment.status(effectiveDate));
        }
    }

    @Transactional
    public void addLateFee(Long loanRequestId, Integer installmentNumber, BigDecimal feeDelta) {
        if (loanRequestId == null
                || installmentNumber == null
                || feeDelta == null
                || feeDelta.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        loanInstallmentRepository.addScheduledFee(
                loanRequestId,
                installmentNumber,
                feeDelta.setScale(2, RoundingMode.HALF_UP));
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
        LocalDate paidDate = paidAt != null ? paidAt.atZone(SYSTEM_ZONE).toLocalDate() : LocalDate.now();
        BigDecimal remaining = money(rawAmountPaid);
        boolean settlementMode = false;
        for (MutableInstallment installment : installments) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                return;
            }
            if (installment.remainingAmount().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            if (settlementMode && installment.dueDate.isAfter(paidDate)) {
                installment.waiveRemainingInterest();
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
            if (!settlementMode
                    && remaining.compareTo(BigDecimal.ZERO) > 0
                    && installment.remainingAmount().compareTo(BigDecimal.ZERO) <= 0) {
                settlementMode = true;
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
        private final LocalDate dueDate;
        private final BigDecimal scheduledPrincipal;
        private final BigDecimal scheduledInterest;
        private BigDecimal waivedInterest;
        private final BigDecimal scheduledFee;
        private BigDecimal paidPrincipal;
        private BigDecimal paidInterest;
        private BigDecimal paidFee;
        private Instant lastPaidAt;

        private MutableInstallment(
                Long id,
                LocalDate dueDate,
                BigDecimal scheduledPrincipal,
                BigDecimal scheduledInterest,
                BigDecimal waivedInterest,
                BigDecimal scheduledFee,
                BigDecimal paidPrincipal,
                BigDecimal paidInterest,
                BigDecimal paidFee,
                Instant lastPaidAt) {
            this.id = id;
            this.dueDate = dueDate;
            this.scheduledPrincipal = scheduledPrincipal;
            this.scheduledInterest = scheduledInterest;
            this.waivedInterest = waivedInterest;
            this.scheduledFee = scheduledFee;
            this.paidPrincipal = paidPrincipal;
            this.paidInterest = paidInterest;
            this.paidFee = paidFee;
            this.lastPaidAt = lastPaidAt;
        }

        private static MutableInstallment from(LoanInstallment installment) {
            return new MutableInstallment(
                    installment.id(),
                    installment.dueDate(),
                    installment.scheduledPrincipal(),
                    installment.scheduledInterest(),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
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
            return scheduledPrincipal
                    .add(chargeableInterest())
                    .add(scheduledFee)
                    .subtract(totalPaid())
                    .max(BigDecimal.ZERO);
        }

        private BigDecimal remainingPrincipal() {
            return scheduledPrincipal.subtract(paidPrincipal).max(BigDecimal.ZERO);
        }

        private BigDecimal remainingInterest() {
            return chargeableInterest().subtract(paidInterest).max(BigDecimal.ZERO);
        }

        private BigDecimal remainingFee() {
            return scheduledFee.subtract(paidFee).max(BigDecimal.ZERO);
        }

        private BigDecimal chargeableInterest() {
            return scheduledInterest.subtract(waivedInterest).max(BigDecimal.ZERO);
        }

        private void waiveRemainingInterest() {
            BigDecimal remainingChargeableInterest = remainingInterest();
            if (remainingChargeableInterest.compareTo(BigDecimal.ZERO) <= 0) {
                return;
            }
            waivedInterest = waivedInterest.add(remainingChargeableInterest).setScale(2, RoundingMode.HALF_UP);
        }

        private LoanInstallmentStatus status(LocalDate asOfDate) {
            if (remainingAmount().compareTo(BigDecimal.ZERO) <= 0) {
                return LoanInstallmentStatus.PAID;
            }
            if (dueDate != null && dueDate.isBefore(asOfDate)) {
                return LoanInstallmentStatus.OVERDUE;
            }
            if (totalPaid().compareTo(BigDecimal.ZERO) <= 0) {
                return LoanInstallmentStatus.PENDING;
            }
            return LoanInstallmentStatus.PARTIALLY_PAID;
        }
    }
}
