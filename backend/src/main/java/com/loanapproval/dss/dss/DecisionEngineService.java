package com.loanapproval.dss.dss;

import java.math.BigDecimal;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class DecisionEngineService {

    public DssResult evaluate(DecisionInput input) {
        double monthlyIncome = toPositiveDouble(input.monthlyIncome());
        double debtToIncomeRatio = normalizeDti(input.debtToIncomeRatio());
        double requestedAmount = toPositiveDouble(input.requestedAmount());
        double burdenRatio = monthlyIncome > 0 ? requestedAmount / monthlyIncome : 12.0;

        double dtiScore = dtiScore(debtToIncomeRatio);
        double incomeScore = incomeScore(monthlyIncome);
        double burdenScore = burdenScore(burdenRatio);
        double employmentScore = employmentScore(input.employmentStatus());

        double weightedRawScore =
            dtiScore * 0.40 +
            incomeScore * 0.25 +
            burdenScore * 0.20 +
            employmentScore * 0.15;

        int creditScore = clamp((int) Math.round(300 + weightedRawScore * 5.5), 300, 850);
        RiskRank riskRank = riskRank(creditScore, debtToIncomeRatio);

        boolean lowDti = debtToIncomeRatio <= 35;
        boolean borderline = isBorderline(input, monthlyIncome, debtToIncomeRatio, burdenRatio, riskRank);

        DssRecommendation recommendation = recommendation(riskRank, lowDti, borderline, debtToIncomeRatio);
        CustomerSegment customerSegment = customerSegment(riskRank, monthlyIncome, requestedAmount, debtToIncomeRatio);

        String appliedRule = appliedRule(recommendation, riskRank, lowDti, borderline, debtToIncomeRatio);
        String explanation = String.format(
            Locale.US,
            "Score=%d (DTI %.1f%%, income %.0f, burden %.2f, employment %.0f). Risk=%s, Segment=%s, Rule=%s.",
            creditScore,
            debtToIncomeRatio,
            monthlyIncome,
            burdenRatio,
            employmentScore,
            riskRank.name(),
            customerSegment.name(),
            appliedRule
        );

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
        if (income >= 5000) {
            return 100;
        }
        if (income >= 3000) {
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

    private double burdenScore(double burdenRatio) {
        if (burdenRatio <= 3) {
            return 100;
        }
        if (burdenRatio <= 6) {
            return 78;
        }
        if (burdenRatio <= 10) {
            return 52;
        }
        if (burdenRatio <= 15) {
            return 34;
        }
        return 16;
    }

    private double employmentScore(String employmentStatus) {
        if (employmentStatus == null || employmentStatus.isBlank()) {
            return 50;
        }

        String normalized = employmentStatus.trim().toUpperCase(Locale.ROOT);
        if (normalized.contains("UNEMPLOY")) {
            return 20;
        }
        if (normalized.contains("FULL") || normalized.contains("PERMANENT") || normalized.contains("EMPLOYED")) {
            return 82;
        }
        if (normalized.contains("SELF") || normalized.contains("BUSINESS") || normalized.contains("OWNER")) {
            return 68;
        }
        if (normalized.contains("PART") || normalized.contains("TEMP") || normalized.contains("CONTRACT")) {
            return 52;
        }
        return 56;
    }

    private RiskRank riskRank(int creditScore, double dti) {
        if (dti >= 75) {
            return RiskRank.D;
        }

        RiskRank rank;
        if (creditScore >= 760) {
            rank = RiskRank.A;
        } else if (creditScore >= 680) {
            rank = RiskRank.B;
        } else if (creditScore >= 600) {
            rank = RiskRank.C;
        } else {
            rank = RiskRank.D;
        }

        if (dti > 55 && (rank == RiskRank.A || rank == RiskRank.B)) {
            return RiskRank.C;
        }
        if (dti > 45 && rank == RiskRank.A) {
            return RiskRank.B;
        }
        return rank;
    }

    private boolean isBorderline(
        DecisionInput input,
        double income,
        double dti,
        double burdenRatio,
        RiskRank rank
    ) {
        if (!(rank == RiskRank.B || rank == RiskRank.C)) {
            return false;
        }
        boolean missingCriticalData = input.monthlyIncome() == null || input.debtToIncomeRatio() == null;
        return (dti >= 35 && dti <= 55) || burdenRatio >= 8 || income < 1500 || missingCriticalData;
    }

    private DssRecommendation recommendation(
        RiskRank riskRank,
        boolean lowDti,
        boolean borderline,
        double dti
    ) {
        if (riskRank == RiskRank.A && lowDti) {
            return DssRecommendation.APPROVE_RECOMMENDED;
        }
        if (riskRank == RiskRank.D) {
            return DssRecommendation.REJECT_RECOMMENDED;
        }
        if ((riskRank == RiskRank.B || riskRank == RiskRank.C) && borderline) {
            return DssRecommendation.ESCALATE_RECOMMENDED;
        }
        if (riskRank == RiskRank.B && dti <= 45) {
            return DssRecommendation.APPROVE_RECOMMENDED;
        }
        if (riskRank == RiskRank.C && dti > 60) {
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
        DssRecommendation recommendation,
        RiskRank riskRank,
        boolean lowDti,
        boolean borderline,
        double dti
    ) {
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

    private double toPositiveDouble(BigDecimal value) {
        if (value == null) {
            return 0;
        }
        return Math.max(0, value.doubleValue());
    }

    private double normalizeDti(BigDecimal dtiValue) {
        if (dtiValue == null) {
            return 50;
        }
        return Math.max(0, Math.min(100, dtiValue.doubleValue()));
    }

    private int clamp(int value, int min, int max) {
        return Math.min(max, Math.max(min, value));
    }
}
