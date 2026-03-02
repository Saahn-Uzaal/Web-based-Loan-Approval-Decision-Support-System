package com.loanapproval.dss.dss;

import com.loanapproval.dss.loan.LoanPurpose;
import java.math.BigDecimal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DecisionEngineServiceTest {

    private final DecisionEngineService decisionEngineService = new DecisionEngineService();

    @Test
    void shouldRecommendApproveForALowDtiCase() {
        DecisionInput input = new DecisionInput(
            1L,
            BigDecimal.valueOf(6000),
            BigDecimal.valueOf(18),
            "Permanent",
            BigDecimal.valueOf(12000),
            18,
            LoanPurpose.HOME
        );

        DssResult result = decisionEngineService.evaluate(input);

        Assertions.assertEquals(RiskRank.A, result.riskRank());
        Assertions.assertEquals(DssRecommendation.APPROVE_RECOMMENDED, result.recommendation());
        Assertions.assertTrue(result.creditScore() >= 760);
    }

    @Test
    void shouldRecommendRejectForHighRiskDCase() {
        DecisionInput input = new DecisionInput(
            2L,
            BigDecimal.valueOf(900),
            BigDecimal.valueOf(78),
            "Unemployed",
            BigDecimal.valueOf(25000),
            36,
            LoanPurpose.BUSINESS
        );

        DssResult result = decisionEngineService.evaluate(input);

        Assertions.assertEquals(RiskRank.D, result.riskRank());
        Assertions.assertEquals(DssRecommendation.REJECT_RECOMMENDED, result.recommendation());
        Assertions.assertTrue(result.creditScore() < 600);
    }

    @Test
    void shouldRecommendEscalateForBorderlineBandC() {
        DecisionInput input = new DecisionInput(
            3L,
            BigDecimal.valueOf(3000),
            BigDecimal.valueOf(45),
            "Contract",
            BigDecimal.valueOf(15000),
            24,
            LoanPurpose.PERSONAL
        );

        DssResult result = decisionEngineService.evaluate(input);

        Assertions.assertTrue(result.riskRank() == RiskRank.B || result.riskRank() == RiskRank.C);
        Assertions.assertEquals(DssRecommendation.ESCALATE_RECOMMENDED, result.recommendation());
    }
}
