package com.loanapproval.dss.debt;

import com.loanapproval.dss.profile.CustomerProfileRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

@Service
public class CustomerDebtService {

    private final CustomerDebtRepository customerDebtRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final com.loanapproval.dss.loan.LoanRepository loanRepository;

    public CustomerDebtService(
        CustomerDebtRepository customerDebtRepository,
        CustomerProfileRepository customerProfileRepository,
        com.loanapproval.dss.loan.LoanRepository loanRepository
    ) {
        this.customerDebtRepository = customerDebtRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.loanRepository = loanRepository;
    }

    public BigDecimal recalculateAndSyncDti(Long customerId) {
        BigDecimal income = customerProfileRepository.findEffectiveMonthlyIncomeByUserId(customerId).orElse(null);
        if (income == null || income.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal activeDebt = totalMonthlyObligations(customerId);
        BigDecimal dti = calculateDtiPercent(activeDebt, income);
        customerProfileRepository.updateDebtToIncomeRatio(customerId, dti);
        return dti;
    }

    public BigDecimal sumActiveMonthlyDebt(Long customerId) {
        return totalMonthlyObligations(customerId);
    }

    public int countActiveDebts(Long customerId) {
        return customerDebtRepository.countVerifiedDebts(customerId);
    }

    public void markPendingAsVerified(Long customerId, Long staffUserId, String note) {
        customerDebtRepository.markPendingAsVerified(customerId, staffUserId, note);
    }

    public void markPendingAsRejected(Long customerId, Long staffUserId, String note) {
        customerDebtRepository.markPendingAsRejected(customerId, staffUserId, note);
    }

    private BigDecimal calculateDtiPercent(BigDecimal totalDebt, BigDecimal monthlyIncome) {
        if (monthlyIncome == null || monthlyIncome.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return totalDebt
            .multiply(BigDecimal.valueOf(100))
            .divide(monthlyIncome, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal totalMonthlyObligations(Long customerId) {
        return customerDebtRepository
            .sumActiveMonthlyDebt(customerId)
            .add(loanRepository.sumCommittedMonthlyPaymentByCustomerId(customerId));
    }
}
