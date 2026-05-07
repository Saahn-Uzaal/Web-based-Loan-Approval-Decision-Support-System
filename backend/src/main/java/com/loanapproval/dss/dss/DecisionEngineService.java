package com.loanapproval.dss.dss;

import com.loanapproval.dss.loan.LoanPurpose;
import com.loanapproval.dss.policy.CreditPolicyDefinition;
import com.loanapproval.dss.policy.CreditPolicyService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DecisionEngineService {

    private static final Logger log = LoggerFactory.getLogger(DecisionEngineService.class);

    private final CreditPolicyService creditPolicyService;

    @Autowired
    public DecisionEngineService(CreditPolicyService creditPolicyService) {
        this.creditPolicyService = creditPolicyService;
    }

    DecisionEngineService() {
        this.creditPolicyService = null;
    }

    public DssResult evaluate(DecisionInput input) {
        CreditPolicyDefinition policy = currentPolicy();
        double monthlyIncome = toPositiveDouble(input.monthlyIncome());
        double requestedAmount = toPositiveDouble(input.requestedAmount());
        int termMonths = sanitizeTermMonths(input.termMonths());
        double existingMonthlyDebt = toPositiveDouble(input.existingMonthlyDebt());
        double monthlyInstallment = input.projectedMonthlyPayment() != null
                ? toPositiveDouble(input.projectedMonthlyPayment())
                : estimateMonthlyInstallment(requestedAmount, termMonths);
        double totalMonthlyDebt = existingMonthlyDebt + monthlyInstallment;
        double debtToIncomeRatio = resolveDti(input.debtToIncomeRatio(), monthlyIncome, totalMonthlyDebt);
        double loanToAnnualIncomeRatio = monthlyIncome > 0 ? requestedAmount / (monthlyIncome * 12.0) : 5.0;

        double dtiScore = dtiScore(debtToIncomeRatio, policy);
        double incomeScore = incomeScore(monthlyIncome);
        double burdenScore = burdenScore(loanToAnnualIncomeRatio);
        double employmentScore = employmentScore(
                input.employmentStatus(),
                employmentYears(input.employmentStartDate()));
        double ageScore = ageScore(input.dateOfBirth());
        double creditHistoryScore = creditHistoryScore(input.creditHistoryScore());
        double collateralScore = collateralScore(input.collateralValue(), requestedAmount);
        double purposeScore = purposeScore(input.purpose());
        double verificationScore = verificationScore(input);

        double weightedRawScore = dtiScore * policy.dssWeightDti()
                + incomeScore * policy.dssWeightIncome()
                + burdenScore * policy.dssWeightBurden()
                + employmentScore * policy.dssWeightEmployment()
                + ageScore * policy.dssWeightAge()
                + creditHistoryScore * policy.dssWeightCreditHistory()
                + collateralScore * policy.dssWeightCollateral()
                + purposeScore * policy.dssWeightPurpose()
                + verificationScore * policy.dssWeightVerification();

        int baseScore = clamp(
                (int) Math.round(policy.dssScoreMin() + weightedRawScore * policy.dssScoreMultiplier()),
                policy.dssScoreMin(),
                policy.dssScoreMax());

        int paymentBonus = paymentRatingBonus(input.paymentRating());
        int compliancePenalty = compliancePenalty(input);
        int creditScore = clamp(
                baseScore + paymentBonus + compliancePenalty,
                policy.dssScoreMin(),
                policy.dssScoreMax());

        RiskRank riskRank = riskRank(creditScore, debtToIncomeRatio, input, policy);
        boolean lowDti = debtToIncomeRatio <= policy.dtiLowThreshold();
        boolean borderline = isBorderline(monthlyIncome, debtToIncomeRatio, loanToAnnualIncomeRatio, riskRank);

        DssRecommendation recommendation = recommendation(input, riskRank, debtToIncomeRatio, policy);
        CustomerSegment customerSegment = customerSegment(riskRank, monthlyIncome, requestedAmount, debtToIncomeRatio);
        String appliedRule = appliedRule(input, recommendation, riskRank, lowDti, borderline, debtToIncomeRatio, policy);
        String explanation = String.format(
                Locale.US,
                "Điểm=%d (nền=%d, thưởng thanh toán=%+d, phạt tuân thủ=%+d, DTI %.1f%%, thu nhập %.0f, tỷ lệ vay/thu nhập %.2fx, việc làm %.0f, tuổi %.0f, lịch sử tín dụng %.0f, tài sản bảo đảm %.0f, xác minh %.0f). Rủi ro=%s, phân khúc=%s, quy tắc=%s.",
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
                appliedRule);

        log.info(
                "DSS evaluation: customerId={}, score={}, rank={}, recommendation={}",
                input.customerId(),
                creditScore,
                riskRank,
                recommendation);

        return new DssResult(creditScore, riskRank, customerSegment, recommendation, explanation);
    }

    private double dtiScore(double dti, CreditPolicyDefinition policy) {
        if (dti <= 20) {
            return 100;
        }
        if (dti <= policy.dtiLowThreshold()) {
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
        if (income >= 60_000_000) {
            return 100;
        }
        if (income >= 40_000_000) {
            return 88;
        }
        if (income >= 25_000_000) {
            return 82;
        }
        if (income >= 15_000_000) {
            return 62;
        }
        if (income >= 8_000_000) {
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

    private RiskRank riskRank(int creditScore, double dti, DecisionInput input, CreditPolicyDefinition policy) {
        if (hasComplianceHardReject(input) || dti >= policy.dtiExtremeThreshold()) {
            return RiskRank.D;
        }

        RiskRank rank;
        if (creditScore >= policy.dssRankAThreshold()) {
            rank = RiskRank.A;
        } else if (creditScore >= policy.dssRankBThreshold()) {
            rank = RiskRank.B;
        } else if (creditScore >= policy.dssRankCThreshold()) {
            rank = RiskRank.C;
        } else {
            rank = RiskRank.D;
        }

        if (dti > policy.dtiHighDowngradeThreshold() && (rank == RiskRank.A || rank == RiskRank.B)) {
            return RiskRank.C;
        }
        if (dti > policy.dtiModerateDowngradeThreshold() && rank == RiskRank.A) {
            return RiskRank.B;
        }
        return rank;
    }

    private boolean isBorderline(
            double income,
            double dti,
            double loanToAnnualIncomeRatio,
            RiskRank rank) {
        if (!(rank == RiskRank.B || rank == RiskRank.C)) {
            return false;
        }
        return (dti >= 35 && dti <= 60)
                || loanToAnnualIncomeRatio >= 2.5
                || income < 15_000_000;
    }

    private DssRecommendation recommendation(
            DecisionInput input,
            RiskRank riskRank,
            double dti,
            CreditPolicyDefinition policy) {
        if (hasComplianceHardReject(input) || riskRank == RiskRank.D) {
            return DssRecommendation.REJECT_RECOMMENDED;
        }
        if (riskRank == RiskRank.C && dti > policy.dtiRejectThreshold()) {
            return DssRecommendation.REJECT_RECOMMENDED;
        }
        if (isUnsecuredApplication(input)) {
            return DssRecommendation.MANUAL_REVIEW_RECOMMENDED;
        }
        return DssRecommendation.APPROVE_RECOMMENDED;
    }

    private CustomerSegment customerSegment(
            RiskRank riskRank,
            double monthlyIncome,
            double requestedAmount,
            double dti) {
        boolean highRisk = riskRank == RiskRank.C || riskRank == RiskRank.D || dti >= 50;
        boolean highValue = monthlyIncome >= 40_000_000 || requestedAmount >= 300_000_000;

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
            double dti,
            CreditPolicyDefinition policy) {
        if (hasComplianceHardReject(input)) {
            return "Từ chối cứng theo quy tắc tuân thủ";
        }
        if (recommendation == DssRecommendation.REJECT_RECOMMENDED && riskRank == RiskRank.D) {
            return "Xếp hạng rủi ro D";
        }
        if (recommendation == DssRecommendation.REJECT_RECOMMENDED
                && riskRank == RiskRank.C
                && dti > policy.dtiRejectThreshold()) {
            return "Xếp hạng rủi ro C với DTI rất cao";
        }
        if (recommendation == DssRecommendation.MANUAL_REVIEW_RECOMMENDED) {
            return "Hồ sơ vay tín chấp đạt ngưỡng tiếp nhận nhưng phải qua thẩm định thủ công";
        }
        if (recommendation == DssRecommendation.APPROVE_RECOMMENDED && riskRank == RiskRank.A && lowDti) {
            return "Hạng A với DTI thấp";
        }
        if (recommendation == DssRecommendation.APPROVE_RECOMMENDED && riskRank == RiskRank.B) {
            return "Hạng B cần nhân viên thẩm định";
        }
        if (borderline || Boolean.FALSE.equals(input.incomeVerified())) {
            return "Chưa tự động từ chối, giữ lại trong hàng chờ thẩm định";
        }
        return "Khuyến nghị duyệt theo quy tắc an toàn mặc định";
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
        return Boolean.TRUE.equals(input.fraudFlag())
                || Boolean.TRUE.equals(input.kycFailed())
                || Boolean.TRUE.equals(input.amlFailed());
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

    private boolean isUnsecuredApplication(DecisionInput input) {
        return input.collateralValue() == null || input.collateralValue().compareTo(BigDecimal.ZERO) <= 0;
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

    private CreditPolicyDefinition currentPolicy() {
        if (creditPolicyService == null) {
            return CreditPolicyDefinition.defaultPolicy();
        }
        return creditPolicyService.currentPolicy();
    }
}
