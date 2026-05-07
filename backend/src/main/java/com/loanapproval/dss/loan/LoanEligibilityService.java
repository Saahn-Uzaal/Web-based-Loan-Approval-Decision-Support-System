package com.loanapproval.dss.loan;

import com.loanapproval.dss.dss.RiskRank;
import com.loanapproval.dss.policy.CreditPolicyDefinition;
import com.loanapproval.dss.policy.CreditPolicyService;
import com.loanapproval.dss.profile.CustomerProfile;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LoanEligibilityService {

    private static final MathContext MATH_CONTEXT = new MathContext(18, RoundingMode.HALF_UP);
    @Deprecated(forRemoval = false)
    public static final String POLICY_VERSION = CreditPolicyDefinition.defaultPolicy().version();

    private final CreditPolicyService creditPolicyService;

    @Autowired
    public LoanEligibilityService(CreditPolicyService creditPolicyService) {
        this.creditPolicyService = creditPolicyService;
    }

    LoanEligibilityService() {
        this.creditPolicyService = null;
    }

    public LoanEligibilityResult evaluate(
            CustomerProfile profile,
            BigDecimal existingMonthlyDebt,
            LoanType loanType,
            BigDecimal requestedAmount,
            Integer requestedTermMonths,
            BigDecimal collateralValue,
            RiskRank riskRank) {
        CreditPolicyDefinition policy = currentPolicy();
        return evaluateWithActualTerms(
                profile,
                existingMonthlyDebt,
                loanType,
                requestedAmount,
                requestedTermMonths,
                defaultAnnualRate(loanType, policy),
                collateralValue,
                riskRank);
    }

    public LoanEligibilityResult evaluateWithActualTerms(
            CustomerProfile profile,
            BigDecimal existingMonthlyDebt,
            LoanType loanType,
            BigDecimal requestedAmount,
            Integer requestedTermMonths,
            BigDecimal annualRateOverride,
            BigDecimal collateralValue,
            RiskRank riskRank) {
        CreditPolicyDefinition policy = currentPolicy();
        BigDecimal income = positive(profile != null ? profile.effectiveMonthlyIncome() : null);
        BigDecimal debt = nonNegative(existingMonthlyDebt);
        int termMonths = sanitizeTerm(requestedTermMonths);
        BigDecimal annualRate = annualRateOverride != null
                ? annualRateOverride.setScale(6, RoundingMode.HALF_UP)
                : defaultAnnualRate(loanType, policy).setScale(6, RoundingMode.HALF_UP);

        List<BigDecimal> caps = new ArrayList<>();
        List<String> reasons = new ArrayList<>();

        BigDecimal productCap = loanType == LoanType.SECURED
                ? policy.securedProductCap()
                : policy.unsecuredProductCap();
        caps.add(productCap);
        reasons.add("productCap=" + productCap.toPlainString());

        if (income != null) {
            BigDecimal capacity = income.multiply(policy.maxDsr(), MATH_CONTEXT)
                    .subtract(debt, MATH_CONTEXT)
                    .max(BigDecimal.ZERO);
            BigDecimal cashflowCap = presentValue(capacity, annualRate, termMonths);
            BigDecimal incomeMultipleCap = income.multiply(policy.unsecuredIncomeMultiple(), MATH_CONTEXT);
            caps.add(cashflowCap);
            caps.add(incomeMultipleCap);
            reasons.add("cashflowCap=" + cashflowCap.toPlainString());
            reasons.add("incomeMultipleCap=" + incomeMultipleCap.toPlainString());
        }

        if (loanType == LoanType.SECURED && collateralValue != null && collateralValue.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal ltvCap = collateralValue.multiply(policy.securedVehicleLtv(), MATH_CONTEXT);
            caps.add(ltvCap);
            reasons.add("ltvCap=" + ltvCap.toPlainString());
        }

        BigDecimal baseLimit = caps.stream()
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        BigDecimal eligibleLimit = baseLimit
                .multiply(riskAdjustment(riskRank, policy), MATH_CONTEXT)
                .setScale(0, RoundingMode.HALF_UP);

        BigDecimal safeRequested = nonNegative(requestedAmount);
        BigDecimal approvedAmount = safeRequested.min(eligibleLimit).setScale(0, RoundingMode.HALF_UP);
        BigDecimal approvedMonthlyPayment = approvedAmount.compareTo(BigDecimal.ZERO) > 0
                ? calculateMonthlyPayment(approvedAmount, termMonths, annualRate)
                : BigDecimal.ZERO.setScale(0, RoundingMode.HALF_UP);

        return new LoanEligibilityResult(
                eligibleLimit,
                approvedAmount,
                termMonths,
                annualRate.setScale(6, RoundingMode.HALF_UP),
                approvedMonthlyPayment,
                policy.version(),
                String.join(", ", reasons));
    }

    public LoanEligibilityResult fromStoredLimit(LoanRecord loan) {
        CreditPolicyDefinition policy = currentPolicy();
        BigDecimal limit = loan.eligibleLimit() != null ? loan.eligibleLimit() : loan.amount();
        BigDecimal approvedAmount = loan.amount().min(limit).setScale(0, RoundingMode.HALF_UP);
        Integer termMonths = sanitizeTerm(loan.termMonths());
        BigDecimal annualRate = defaultAnnualRate(loan.loanType(), policy);
        return new LoanEligibilityResult(
                limit.setScale(0, RoundingMode.HALF_UP),
                approvedAmount,
                termMonths,
                annualRate.setScale(6, RoundingMode.HALF_UP),
                calculateMonthlyPayment(approvedAmount, termMonths, annualRate),
                policy.version(),
                "storedEligibleLimit=" + limit.toPlainString());
    }

    public BigDecimal calculateMonthlyPayment(BigDecimal principalAmount, Integer termMonths, BigDecimal annualInterestRate) {
        if (principalAmount == null || principalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(0, RoundingMode.HALF_UP);
        }
        int months = sanitizeTerm(termMonths);
        BigDecimal annualRate = annualInterestRate != null ? annualInterestRate : BigDecimal.ZERO;
        BigDecimal monthlyRate = annualRate.divide(BigDecimal.valueOf(12), 12, RoundingMode.HALF_UP);
        if (monthlyRate.compareTo(BigDecimal.ZERO) == 0) {
            return principalAmount.divide(BigDecimal.valueOf(months), 0, RoundingMode.HALF_UP);
        }

        BigDecimal onePlusRPowerN = BigDecimal.ONE.add(monthlyRate, MATH_CONTEXT).pow(months, MATH_CONTEXT);
        BigDecimal numerator = principalAmount.multiply(monthlyRate, MATH_CONTEXT).multiply(onePlusRPowerN, MATH_CONTEXT);
        BigDecimal denominator = onePlusRPowerN.subtract(BigDecimal.ONE, MATH_CONTEXT);
        return numerator.divide(denominator, 0, RoundingMode.HALF_UP);
    }

    public BigDecimal defaultAnnualRate(LoanType loanType) {
        return defaultAnnualRate(loanType, currentPolicy());
    }

    public BigDecimal calculateAffordablePrincipal(
            BigDecimal monthlyCapacity,
            Integer termMonths,
            BigDecimal annualInterestRate) {
        return presentValue(
                monthlyCapacity,
                annualInterestRate != null ? annualInterestRate : BigDecimal.ZERO,
                sanitizeTerm(termMonths));
    }

    public String currentPolicyVersion() {
        return currentPolicy().version();
    }

    private BigDecimal presentValue(BigDecimal monthlyCapacity, BigDecimal annualRate, int termMonths) {
        if (monthlyCapacity == null || monthlyCapacity.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal monthlyRate = annualRate.divide(BigDecimal.valueOf(12), 12, RoundingMode.HALF_UP);
        if (monthlyRate.compareTo(BigDecimal.ZERO) == 0) {
            return monthlyCapacity.multiply(BigDecimal.valueOf(termMonths), MATH_CONTEXT);
        }
        BigDecimal onePlusRPowerN = BigDecimal.ONE.add(monthlyRate, MATH_CONTEXT).pow(termMonths, MATH_CONTEXT);
        BigDecimal discountFactor = BigDecimal.ONE.subtract(
                BigDecimal.ONE.divide(onePlusRPowerN, MATH_CONTEXT),
                MATH_CONTEXT);
        return monthlyCapacity.multiply(discountFactor, MATH_CONTEXT).divide(monthlyRate, 0, RoundingMode.HALF_UP);
    }

    private BigDecimal riskAdjustment(RiskRank riskRank, CreditPolicyDefinition policy) {
        if (riskRank == null) {
            return policy.riskAdjustmentB();
        }
        return switch (riskRank) {
            case A -> policy.riskAdjustmentA();
            case B -> policy.riskAdjustmentB();
            case C -> policy.riskAdjustmentC();
            case D -> policy.riskAdjustmentD();
        };
    }

    private int sanitizeTerm(Integer termMonths) {
        if (termMonths == null || termMonths <= 0) {
            return 12;
        }
        return Math.min(termMonths, 360);
    }

    private BigDecimal positive(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return value;
    }

    private BigDecimal nonNegative(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return value;
    }

    private CreditPolicyDefinition currentPolicy() {
        if (creditPolicyService == null) {
            return CreditPolicyDefinition.defaultPolicy();
        }
        return creditPolicyService.currentPolicy();
    }

    private BigDecimal defaultAnnualRate(LoanType loanType, CreditPolicyDefinition policy) {
        return loanType == LoanType.SECURED
                ? policy.securedAnnualRate()
                : policy.unsecuredAnnualRate();
    }
}
