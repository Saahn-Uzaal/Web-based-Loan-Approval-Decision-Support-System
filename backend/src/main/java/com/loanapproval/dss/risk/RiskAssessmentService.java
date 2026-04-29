package com.loanapproval.dss.risk;

import com.loanapproval.dss.dss.DecisionInput;
import com.loanapproval.dss.dss.DssResult;
import com.loanapproval.dss.dss.RiskRank;
import com.loanapproval.dss.verification.CustomerVerification;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RiskAssessmentService {

    private final RiskAssessmentRepository riskAssessmentRepository;

    public RiskAssessmentService(RiskAssessmentRepository riskAssessmentRepository) {
        this.riskAssessmentRepository = riskAssessmentRepository;
    }

    public RiskAssessment evaluateAndSave(
        Long loanRequestId,
        DecisionInput input,
        DssResult dssResult,
        CustomerVerification verification
    ) {
        RiskAssessment snapshot = evaluate(loanRequestId, input, dssResult, verification);
        riskAssessmentRepository.upsert(snapshot);
        return snapshot;
    }

    public RiskAssessment evaluate(
        Long loanRequestId,
        DecisionInput input,
        DssResult dssResult,
        CustomerVerification verification
    ) {
        int creditRiskScore = calculateCreditRiskScore(input, dssResult);
        int fraudRiskScore = calculateFraudRiskScore(input, verification);
        int operationalRiskScore = calculateOperationalRiskScore(input, verification);

        List<String> reasons = new ArrayList<>();
        if (creditRiskScore >= 70) {
            reasons.add("Rủi ro tín dụng cao");
        }
        if (fraudRiskScore >= 70) {
            reasons.add("Rủi ro gian lận cao");
        }
        if (operationalRiskScore >= 70) {
            reasons.add("Rủi ro vận hành cao");
        }
        if (dssResult.riskRank() == RiskRank.D) {
            reasons.add("DSS xếp hạng D");
        }
        if (reasons.isEmpty()) {
            reasons.add("Không có cờ rủi ro cao");
        }

        RiskLevel overall = resolveOverallRiskLevel(creditRiskScore, fraudRiskScore, operationalRiskScore);
        RiskAssessment snapshot = new RiskAssessment(
            loanRequestId,
            creditRiskScore,
            fraudRiskScore,
            operationalRiskScore,
            overall,
            String.join(", ", reasons),
            java.time.Instant.now()
        );
        return snapshot;
    }

    public void save(RiskAssessment riskAssessment) {
        riskAssessmentRepository.upsert(riskAssessment);
    }

    private int calculateCreditRiskScore(DecisionInput input, DssResult dssResult) {
        int score = switch (dssResult.riskRank()) {
            case A -> 20;
            case B -> 40;
            case C -> 65;
            case D -> 85;
        };

        double dti = input.debtToIncomeRatio() != null ? input.debtToIncomeRatio().doubleValue() : 50.0;
        if (dti > 50) {
            score += 15;
        }
        if (dti > 70) {
            score += 10;
        }

        if (input.creditHistoryScore() != null && input.creditHistoryScore() < 40) {
            score += 10;
        }

        return clamp(score);
    }

    private int calculateFraudRiskScore(DecisionInput input, CustomerVerification verification) {
        int score = 20;
        if (verification.fraudFlag()) {
            score = 95;
        } else {
            if (verification.hasHardRejectFlag()) {
                score += 50;
            }
            if (input.monthlyIncome() != null && input.monthlyIncome().compareTo(BigDecimal.ZERO) <= 0) {
                score += 20;
            }
            if (input.requestedAmount() != null && input.monthlyIncome() != null
                && input.monthlyIncome().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal multiplier = input.requestedAmount().divide(input.monthlyIncome(), 4, java.math.RoundingMode.HALF_UP);
                if (multiplier.compareTo(BigDecimal.valueOf(30)) > 0) {
                    score += 25;
                } else if (multiplier.compareTo(BigDecimal.valueOf(20)) > 0) {
                    score += 12;
                }
            }
        }
        return clamp(score);
    }

    private int calculateOperationalRiskScore(DecisionInput input, CustomerVerification verification) {
        int score = 15;
        if (verification.isPending()) {
            score += 35;
        }
        if (input.monthlyIncome() == null || input.debtToIncomeRatio() == null) {
            score += 20;
        }
        if (input.termMonths() == null || input.termMonths() <= 0) {
            score += 30;
        }
        return clamp(score);
    }

    private RiskLevel resolveOverallRiskLevel(int creditRiskScore, int fraudRiskScore, int operationalRiskScore) {
        int maxScore = Math.max(creditRiskScore, Math.max(fraudRiskScore, operationalRiskScore));
        if (maxScore >= 70) {
            return RiskLevel.HIGH;
        }
        if (maxScore >= 40) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }

    private int clamp(int value) {
        return Math.min(100, Math.max(0, value));
    }
}
