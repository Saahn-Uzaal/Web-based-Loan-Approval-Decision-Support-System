package com.loanapproval.dss.debt;

import com.loanapproval.dss.debt.dto.CreateDebtRequest;
import com.loanapproval.dss.debt.dto.CustomerDebtResponse;
import com.loanapproval.dss.debt.dto.DebtMetricsResponse;
import com.loanapproval.dss.profile.CustomerProfileRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CustomerDebtService {

    private final CustomerDebtRepository customerDebtRepository;
    private final CustomerProfileRepository customerProfileRepository;

    public CustomerDebtService(
        CustomerDebtRepository customerDebtRepository,
        CustomerProfileRepository customerProfileRepository
    ) {
        this.customerDebtRepository = customerDebtRepository;
        this.customerProfileRepository = customerProfileRepository;
    }

    public List<CustomerDebtResponse> listMine(Long customerId) {
        return customerDebtRepository.findByCustomerId(customerId).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public CustomerDebtResponse create(Long customerId, CreateDebtRequest request) {
        CustomerDebt created = customerDebtRepository.create(
            customerId,
            request.debtType().trim(),
            request.monthlyPayment(),
            request.remainingBalance(),
            sanitizeLenderName(request.lenderName())
        );
        recalculateAndSyncDti(customerId);
        return toResponse(created);
    }

    @Transactional
    public void delete(Long customerId, Long debtId) {
        int deleted = customerDebtRepository.deleteOwned(debtId, customerId);
        if (deleted == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Debt item not found");
        }
        recalculateAndSyncDti(customerId);
    }

    public DebtMetricsResponse metrics(Long customerId, BigDecimal newLoanMonthlyPayment) {
        BigDecimal income = customerProfileRepository.findMonthlyIncomeByUserId(customerId).orElse(BigDecimal.ZERO);
        BigDecimal activeDebt = customerDebtRepository.sumActiveMonthlyDebt(customerId);
        BigDecimal baseDti = calculateDtiPercent(activeDebt, income);
        BigDecimal baseDscr = calculateDscr(income, activeDebt);

        BigDecimal projectedDebt = activeDebt.add(nonNegative(newLoanMonthlyPayment));
        BigDecimal projectedDti = calculateDtiPercent(projectedDebt, income);
        BigDecimal projectedDscr = calculateDscr(income, projectedDebt);

        return new DebtMetricsResponse(
            income,
            activeDebt,
            baseDti,
            baseDscr,
            nonNegative(newLoanMonthlyPayment),
            projectedDti,
            projectedDscr
        );
    }

    public BigDecimal recalculateAndSyncDti(Long customerId) {
        BigDecimal income = customerProfileRepository.findMonthlyIncomeByUserId(customerId).orElse(null);
        if (income == null || income.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal activeDebt = customerDebtRepository.sumActiveMonthlyDebt(customerId);
        BigDecimal dti = calculateDtiPercent(activeDebt, income);
        customerProfileRepository.updateDebtToIncomeRatio(customerId, dti);
        return dti;
    }

    public BigDecimal sumActiveMonthlyDebt(Long customerId) {
        return customerDebtRepository.sumActiveMonthlyDebt(customerId);
    }

    private CustomerDebtResponse toResponse(CustomerDebt debt) {
        return new CustomerDebtResponse(
            debt.id(),
            debt.debtType(),
            debt.monthlyPayment(),
            debt.remainingBalance(),
            debt.lenderName(),
            debt.status(),
            debt.createdAt(),
            debt.updatedAt()
        );
    }

    private BigDecimal calculateDtiPercent(BigDecimal totalDebt, BigDecimal monthlyIncome) {
        if (monthlyIncome == null || monthlyIncome.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return totalDebt
            .multiply(BigDecimal.valueOf(100))
            .divide(monthlyIncome, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateDscr(BigDecimal monthlyIncome, BigDecimal monthlyDebt) {
        if (monthlyDebt == null || monthlyDebt.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        if (monthlyIncome == null || monthlyIncome.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return monthlyIncome.divide(monthlyDebt, 2, RoundingMode.HALF_UP);
    }

    private String sanitizeLenderName(String lenderName) {
        if (lenderName == null || lenderName.isBlank()) {
            return null;
        }
        return lenderName.trim();
    }

    private BigDecimal nonNegative(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return value;
    }
}
