package com.loanapproval.dss.debt;

import com.loanapproval.dss.debt.dto.CreateCustomerDebtRequest;
import com.loanapproval.dss.debt.dto.CustomerDebtMetricsResponse;
import com.loanapproval.dss.debt.dto.CustomerDebtResponse;
import com.loanapproval.dss.profile.CustomerProfile;
import com.loanapproval.dss.profile.CustomerProfileRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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

    public List<CustomerDebtResponse> listMine(Long customerId) {
        return customerDebtRepository.findByCustomerId(customerId).stream()
            .map(this::toResponse)
            .toList();
    }

    public CustomerDebtMetricsResponse getMetrics(Long customerId) {
        List<CustomerDebt> debts = customerDebtRepository.findByCustomerId(customerId);
        int totalDebtCount = debts.size();
        int pendingVerificationCount = countByStatus(debts, DebtStatus.PENDING_VERIFICATION);
        int verifiedDebtCount = countByStatus(debts, DebtStatus.VERIFIED);
        int rejectedDebtCount = countByStatus(debts, DebtStatus.REJECTED);
        BigDecimal verifiedMonthlyDebt = customerDebtRepository.sumActiveMonthlyDebt(customerId);
        BigDecimal totalMonthlyObligation = totalMonthlyObligations(customerId);
        BigDecimal debtToIncomeRatio = customerProfileRepository.findByUserId(customerId)
            .map(CustomerProfile::debtToIncomeRatio)
            .orElse(null);
        return new CustomerDebtMetricsResponse(
            totalDebtCount,
            pendingVerificationCount,
            verifiedDebtCount,
            rejectedDebtCount,
            verifiedMonthlyDebt,
            totalMonthlyObligation,
            debtToIncomeRatio
        );
    }

    public CustomerDebtResponse create(Long customerId, CreateCustomerDebtRequest request) {
        CustomerDebt debt = customerDebtRepository.create(
            customerId,
            normalize(request.debtType()),
            request.monthlyPayment(),
            request.remainingBalance(),
            normalize(request.lenderName())
        );
        return toResponse(debt);
    }

    public void deleteOwned(Long customerId, Long debtId) {
        CustomerDebt debt = customerDebtRepository.findOwnedById(debtId, customerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy khoản nợ cần xóa"));
        if (debt.status() != DebtStatus.PENDING_VERIFICATION) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Chỉ có thể xóa khoản nợ đang chờ xác minh"
            );
        }
        int updated = customerDebtRepository.deleteOwned(debtId, customerId);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Khoản nợ đã thay đổi trong lúc xử lý");
        }
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

    private int countByStatus(List<CustomerDebt> debts, DebtStatus status) {
        return (int) debts.stream().filter(debt -> debt.status() == status).count();
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

    private String normalize(String value) {
        return value == null ? null : value.trim().replaceAll("\\s+", " ");
    }
}
