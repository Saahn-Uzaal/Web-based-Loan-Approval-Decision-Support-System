package com.loanapproval.dss.dss;

import com.loanapproval.dss.loan.LoanPurpose;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DecisionEngineService {

    private static final Logger log = LoggerFactory.getLogger(DecisionEngineService.class);

    // --- Scoring weights ---
    private static final double WEIGHT_DTI = 0.23;
    private static final double WEIGHT_INCOME = 0.18;
    private static final double WEIGHT_CREDIT_HISTORY = 0.15;
    private static final double WEIGHT_BURDEN = 0.12;
    private static final double WEIGHT_EMPLOYMENT = 0.12;
    private static final double WEIGHT_AGE = 0.07;
    private static final double WEIGHT_COLLATERAL = 0.07;
    private static final double WEIGHT_PURPOSE = 0.04;
    private static final double WEIGHT_VERIFICATION = 0.02;

    // --- Credit score range ---
    private static final int SCORE_MIN = 300;
    private static final int SCORE_MAX = 850;
    private static final double SCORE_MULTIPLIER = 5.5;

    // --- Risk rank thresholds ---
    private static final int RANK_A_THRESHOLD = 780;
    private static final int RANK_B_THRESHOLD = 700;
    private static final int RANK_C_THRESHOLD = 620;

    // --- DTI thresholds ---
    private static final double DTI_LOW_THRESHOLD = 35.0;
    private static final double DTI_HIGH_DOWNGRADE_THRESHOLD = 55.0;
    private static final double DTI_MODERATE_DOWNGRADE_THRESHOLD = 45.0;
    private static final double DTI_EXTREME_THRESHOLD = 75.0;
    private static final double DTI_REJECT_THRESHOLD = 60.0;

    public DssResult evaluate(DecisionInput input) {
        double monthlyIncome = toPositiveDouble(input.monthlyIncome());
        double requestedAmount = toPositiveDouble(input.requestedAmount());
        int termMonths = sanitizeTermMonths(input.termMonths());
        double existingMonthlyDebt = toPositiveDouble(input.existingMonthlyDebt());
        double monthlyInstallment = estimateMonthlyInstallment(requestedAmount, termMonths);
        double totalMonthlyDebt = existingMonthlyDebt + monthlyInstallment;
        double debtToIncomeRatio = resolveDti(input.debtToIncomeRatio(), monthlyIncome, totalMonthlyDebt);
        double loanToAnnualIncomeRatio = monthlyIncome > 0 ? requestedAmount / (monthlyIncome * 12.0) : 5.0;

        double dtiScore = dtiScore(debtToIncomeRatio);
        double incomeScore = incomeScore(monthlyIncome);
        double burdenScore = burdenScore(loanToAnnualIncomeRatio);
        double employmentScore = employmentScore(input.employmentStatus(), employmentYears(input.employmentStartDate()));
        double ageScore = ageScore(input.dateOfBirth());
        double creditHistoryScore = creditHistoryScore(input.creditHistoryScore());
        double collateralScore = collateralScore(input.collateralValue(), requestedAmount);
        double purposeScore = purposeScore(input.purpose());
        double verificationScore = verificationScore(input);

        double weightedRawScore =
            dtiScore * WEIGHT_DTI +
            incomeScore * WEIGHT_INCOME +
            burdenScore * WEIGHT_BURDEN +
            employmentScore * WEIGHT_EMPLOYMENT +
            ageScore * WEIGHT_AGE +
            creditHistoryScore * WEIGHT_CREDIT_HISTORY +
            collateralScore * WEIGHT_COLLATERAL +
            purposeScore * WEIGHT_PURPOSE +
            verificationScore * WEIGHT_VERIFICATION;

        int baseScore = clamp((int) Math.round(SCORE_MIN + weightedRawScore * SCORE_MULTIPLIER), SCORE_MIN, SCORE_MAX);

        int paymentBonus = paymentRatingBonus(input.paymentRating());
        int compliancePenalty = compliancePenalty(input);
        int creditScore = clamp(baseScore + paymentBonus + compliancePenalty, 300, 850);

        RiskRank riskRank = riskRank(creditScore, debtToIncomeRatio, input);

        boolean lowDti = debtToIncomeRatio <= DTI_LOW_THRESHOLD;
        boolean borderline = isBorderline(input, monthlyIncome, debtToIncomeRatio, loanToAnnualIncomeRatio, riskRank);

        DssRecommendation recommendation = recommendation(input, riskRank, lowDti, borderline, debtToIncomeRatio);
        CustomerSegment customerSegment = customerSegment(riskRank, monthlyIncome, requestedAmount, debtToIncomeRatio);

        String appliedRule = appliedRule(input, recommendation, riskRank, lowDti, borderline, debtToIncomeRatio);
        String explanation = String.format(
            Locale.US,
            "Score=%d (base=%d, paymentBonus=%+d, compliancePenalty=%+d, DTI %.1f%%, income %.0f, loanToIncome %.2fx, employment %.0f, age %.0f, creditHistory %.0f, collateral %.0f, verification %.0f). Risk=%s, Segment=%s, Rule=%s.",
            creditScore,
            baseScore,
            paymentBonus,
            compliancePenalty,
            debtToIncomeRatio,
            monthlyIncome,
            loanToAnnualIncomeRatio,
            employmentScore,
            ageScore,
            creditHistoryScore,
            collateralScore,
            verificationScore,
            riskRank.name(),
            customerSegment.name(),
            appliedRule
        );

        log.info("DSS evaluation: customerId={}, score={}, rank={}, recommendation={}",
            input.customerId(), creditScore, riskRank, recommendation);

        return new DssResult(creditScore, riskRank, customerSegment, recommendation, explanation);
    }

    private double dtiScore(double dti) {
        if (dti <= 20) {
            return 100;
        }
        if (dti <= 35) {
            return 82;
        }
        if (dti <= 50) {
            return 58;
        }
        if (dti <= 65) {
            return 32;
        }
        return 12;
    }

    private double incomeScore(double income) {
        if (income >= 6000) {
            return 100;
        }
        if (income >= 4000) {
            return 88;
        }
        if (income >= 2500) {
            return 82;
        }
        if (income >= 1500) {
            return 62;
        }
        if (income >= 800) {
            return 42;
        }
        if (income > 0) {
            return 26;
        }
        return 20;
    }

    private double burdenScore(double loanToAnnualIncomeRatio) {
        if (loanToAnnualIncomeRatio <= 1.0) {
            return 100;
        }
        if (loanToAnnualIncomeRatio <= 2.0) {
            return 78;
        }
        if (loanToAnnualIncomeRatio <= 3.5) {
            return 52;
        }
        if (loanToAnnualIncomeRatio <= 5.0) {
            return 34;
        }
        return 16;
    }

    private double employmentScore(String employmentStatus, Double employmentYears) {
        double score = employmentBaseScore(employmentStatus);
        if (employmentYears == null) {
            return score;
        }
        if (employmentYears >= 5) {
            score += 8;
        } else if (employmentYears >= 2) {
            score += 4;
        } else if (employmentYears < 1) {
            score -= 8;
        }
        return Math.max(20, Math.min(95, score));
    }

    private RiskRank riskRank(int creditScore, double dti, DecisionInput input) {
        if (hasComplianceHardReject(input)) {
            return RiskRank.D;
        }
        if (dti >= DTI_EXTREME_THRESHOLD) {
            return RiskRank.D;
        }

        RiskRank rank;
        if (creditScore >= RANK_A_THRESHOLD) {
            rank = RiskRank.A;
        } else if (creditScore >= RANK_B_THRESHOLD) {
            rank = RiskRank.B;
        } else if (creditScore >= RANK_C_THRESHOLD) {
            rank = RiskRank.C;
        } else {
            rank = RiskRank.D;
        }

        if (dti > DTI_HIGH_DOWNGRADE_THRESHOLD && (rank == RiskRank.A || rank == RiskRank.B)) {
            return RiskRank.C;
        }
        if (dti > DTI_MODERATE_DOWNGRADE_THRESHOLD && rank == RiskRank.A) {
            return RiskRank.B;
        }
        return rank;
    }

    private boolean isBorderline(
        DecisionInput input,
        double income,
        double dti,
        double loanToAnnualIncomeRatio,
        RiskRank rank
    ) {
        if (!(rank == RiskRank.B || rank == RiskRank.C)) {
            return false;
        }
        boolean missingCriticalData =
            input.monthlyIncome() == null ||
            input.debtToIncomeRatio() == null ||
            input.creditHistoryScore() == null ||
            input.employmentStartDate() == null;
        return (dti >= 35 && dti <= 60) ||
            loanToAnnualIncomeRatio >= 2.5 ||
            income < 1500 ||
            missingCriticalData;
    }

    private DssRecommendation recommendation(
        DecisionInput input,
        RiskRank riskRank,
        boolean lowDti,
        boolean borderline,
        double dti
    ) {
        if (hasComplianceHardReject(input)) {
            return DssRecommendation.REJECT_RECOMMENDED;
        }
        if (riskRank == RiskRank.D) {
            return DssRecommendation.REJECT_RECOMMENDED;
        }
        if (Boolean.FALSE.equals(input.incomeVerified())) {
            return DssRecommendation.ESCALATE_RECOMMENDED;
        }
        if (riskRank == RiskRank.A && lowDti) {
            return DssRecommendation.APPROVE_RECOMMENDED;
        }
        if ((riskRank == RiskRank.B || riskRank == RiskRank.C) && borderline) {
            return DssRecommendation.ESCALATE_RECOMMENDED;
        }
        if (riskRank == RiskRank.B && dti <= DTI_MODERATE_DOWNGRADE_THRESHOLD) {
            return DssRecommendation.APPROVE_RECOMMENDED;
        }
        if (riskRank == RiskRank.C && dti > DTI_REJECT_THRESHOLD) {
            return DssRecommendation.REJECT_RECOMMENDED;
        }
        return DssRecommendation.ESCALATE_RECOMMENDED;
    }

    private CustomerSegment customerSegment(
        RiskRank riskRank,
        double monthlyIncome,
        double requestedAmount,
        double dti
    ) {
        boolean highRisk = riskRank == RiskRank.C || riskRank == RiskRank.D || dti >= 50;
        boolean highValue = monthlyIncome >= 3000 || requestedAmount >= 20000;

        if (!highRisk && highValue) {
            return CustomerSegment.LOW_RISK_HIGH_VALUE;
        }
        if (!highRisk) {
            return CustomerSegment.LOW_RISK_LOW_VALUE;
        }
        if (highValue) {
            return CustomerSegment.HIGH_RISK_HIGH_VALUE;
        }
        return CustomerSegment.HIGH_RISK_LOW_VALUE;
    }

    private String appliedRule(
        DecisionInput input,
        DssRecommendation recommendation,
        RiskRank riskRank,
        boolean lowDti,
        boolean borderline,
        double dti
    ) {
        if (hasComplianceHardReject(input)) {
            return "Compliance hard-fail (KYC/AML/FRAUD) -> REJECT_RECOMMENDED";
        }
        if (Boolean.FALSE.equals(input.incomeVerified()) && recommendation == DssRecommendation.ESCALATE_RECOMMENDED) {
            return "Income not verified -> ESCALATE_RECOMMENDED";
        }
        if (recommendation == DssRecommendation.APPROVE_RECOMMENDED && riskRank == RiskRank.A && lowDti) {
            return "A + low DTI -> APPROVE_RECOMMENDED";
        }
        if (recommendation == DssRecommendation.REJECT_RECOMMENDED && riskRank == RiskRank.D) {
            return "Risk rank D -> REJECT_RECOMMENDED";
        }
        if (recommendation == DssRecommendation.ESCALATE_RECOMMENDED && borderline) {
            return "B/C + borderline factors -> ESCALATE_RECOMMENDED";
        }
        if (recommendation == DssRecommendation.APPROVE_RECOMMENDED && riskRank == RiskRank.B) {
            return "Risk rank B with acceptable DTI -> APPROVE_RECOMMENDED";
        }
        if (recommendation == DssRecommendation.REJECT_RECOMMENDED && riskRank == RiskRank.C && dti > 60) {
            return "Risk rank C with very high DTI -> REJECT_RECOMMENDED";
        }
        return "Conservative fallback -> ESCALATE_RECOMMENDED";
    }

    private int paymentRatingBonus(Integer paymentRating) {
        if (paymentRating == null) {
            return 0;
        }
        int clamped = Math.min(100, Math.max(-100, paymentRating));
        return (int) Math.round(clamped * 0.20);
    }

    private int compliancePenalty(DecisionInput input) {
        if (Boolean.TRUE.equals(input.fraudFlag())) {
            return -250;
        }
        if (Boolean.TRUE.equals(input.kycFailed()) || Boolean.TRUE.equals(input.amlFailed())) {
            return -180;
        }
        if (Boolean.FALSE.equals(input.incomeVerified())) {
            return -45;
        }
        if (input.incomeVerified() == null) {
            return -10;
        }
        return 0;
    }

    private boolean hasComplianceHardReject(DecisionInput input) {
        return Boolean.TRUE.equals(input.fraudFlag()) ||
            Boolean.TRUE.equals(input.kycFailed()) ||
            Boolean.TRUE.equals(input.amlFailed());
    }

    private double verificationScore(DecisionInput input) {
        if (Boolean.TRUE.equals(input.fraudFlag())) {
            return 0;
        }
        if (Boolean.TRUE.equals(input.kycFailed()) || Boolean.TRUE.equals(input.amlFailed())) {
            return 20;
        }
        if (Boolean.TRUE.equals(input.incomeVerified())) {
            return 85;
        }
        if (Boolean.FALSE.equals(input.incomeVerified())) {
            return 45;
        }
        return 60;
    }

    private double ageScore(LocalDate dateOfBirth) {
        if (dateOfBirth == null) {
            return 55;
        }
        int age = Period.between(dateOfBirth, LocalDate.now()).getYears();
        if (age < 18) {
            return 20;
        }
        if (age <= 20) {
            return 45;
        }
        if (age <= 45) {
            return 85;
        }
        if (age <= 55) {
            return 72;
        }
        if (age <= 65) {
            return 55;
        }
        if (age <= 70) {
            return 40;
        }
        return 28;
    }

    private double creditHistoryScore(Integer creditHistoryScore) {
        if (creditHistoryScore == null) {
            return 55;
        }
        return Math.max(0, Math.min(100, creditHistoryScore));
    }

    private double collateralScore(BigDecimal collateralValue, double requestedAmount) {
        if (requestedAmount <= 0) {
            return 50;
        }
        if (collateralValue == null || collateralValue.compareTo(BigDecimal.ZERO) <= 0) {
            return 35;
        }
        double ltv = requestedAmount / collateralValue.doubleValue() * 100.0;
        if (ltv <= 60) {
            return 95;
        }
        if (ltv <= 75) {
            return 82;
        }
        if (ltv <= 90) {
            return 68;
        }
        if (ltv <= 100) {
            return 55;
        }
        if (ltv <= 120) {
            return 42;
        }
        return 28;
    }

    private double purposeScore(LoanPurpose purpose) {
        if (purpose == null) {
            return 55;
        }
        return switch (purpose) {
            case HOME -> 80;
            case EDUCATION -> 70;
            case BUSINESS -> 60;
            case PERSONAL -> 55;
        };
    }

    private double employmentBaseScore(String employmentStatus) {
        if (employmentStatus == null || employmentStatus.isBlank()) {
            return 50;
        }

        String normalized = employmentStatus.trim().toUpperCase(Locale.ROOT);
        if (normalized.contains("UNEMPLOY")) {
            return 20;
        }
        if (normalized.contains("FULL") || normalized.contains("PERMANENT") || normalized.contains("EMPLOYED")) {
            return 80;
        }
        if (normalized.contains("SELF") || normalized.contains("BUSINESS") || normalized.contains("OWNER")) {
            return 66;
        }
        if (normalized.contains("PART") || normalized.contains("TEMP") || normalized.contains("CONTRACT")) {
            return 52;
        }
        return 56;
    }

    private Double employmentYears(LocalDate employmentStartDate) {
        if (employmentStartDate == null) {
            return null;
        }
        if (employmentStartDate.isAfter(LocalDate.now())) {
            return 0.0;
        }
        Period period = Period.between(employmentStartDate, LocalDate.now());
        return period.getYears() + (period.getMonths() / 12.0);
    }

    private int sanitizeTermMonths(Integer termMonths) {
        if (termMonths == null || termMonths <= 0) {
            return 12;
        }
        return Math.min(termMonths, 360);
    }

    private double estimateMonthlyInstallment(double requestedAmount, int termMonths) {
        if (requestedAmount <= 0 || termMonths <= 0) {
            return 0;
        }
        return requestedAmount / termMonths;
    }

    private double toPositiveDouble(BigDecimal value) {
        if (value == null) {
            return 0;
        }
        return Math.max(0, value.doubleValue());
    }

    private double resolveDti(BigDecimal dtiValue, double monthlyIncome, double totalMonthlyDebt) {
        if (dtiValue != null) {
            return Math.max(0, Math.min(100, dtiValue.doubleValue()));
        }
        if (monthlyIncome > 0) {
            return Math.max(0, Math.min(100, (totalMonthlyDebt / monthlyIncome) * 100));
        }
        return 50;
    }

    private int clamp(int value, int min, int max) {
        return Math.min(max, Math.max(min, value));
    }
}
