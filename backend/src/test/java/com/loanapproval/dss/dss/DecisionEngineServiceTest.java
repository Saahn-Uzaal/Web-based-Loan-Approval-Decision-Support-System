package com.loanapproval.dss.dss;

import com.loanapproval.dss.loan.LoanPurpose;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DecisionEngineServiceTest {

    private final DecisionEngineService decisionEngineService = new DecisionEngineService();

    @Test
    void shouldRecommendApproveForALowDtiCase() {
        DecisionInput input = new DecisionInput(
                1L,
                BigDecimal.valueOf(80_000_000),
                BigDecimal.valueOf(12),
                "Permanent",
                LocalDate.of(1992, 5, 20),
                LocalDate.of(2016, 1, 1),
                100,
                BigDecimal.valueOf(500_000_000),
                BigDecimal.valueOf(2_000_000),
                BigDecimal.valueOf(100_000_000),
                18,
                LoanPurpose.HOME,
                null,
                false,
                false,
                false,
                true,
                BigDecimal.valueOf(6_000_000));

        DssResult result = decisionEngineService.evaluate(input);

        Assertions.assertEquals(RiskRank.A, result.riskRank());
        Assertions.assertEquals(DssRecommendation.APPROVE_RECOMMENDED, result.recommendation());
        Assertions.assertTrue(result.creditScore() >= 760);
    }

    @Test
    void shouldRecommendRejectForHighRiskDCase() {
        DecisionInput input = new DecisionInput(
                2L,
                BigDecimal.valueOf(7_000_000),
                BigDecimal.valueOf(78),
                "Unemployed",
                LocalDate.of(2004, 8, 12),
                null,
                20,
                null,
                BigDecimal.valueOf(3_000_000),
                BigDecimal.valueOf(250_000_000),
                36,
                LoanPurpose.BUSINESS,
                null,
                false,
                false,
                false,
                false,
                BigDecimal.valueOf(9_500_000));

        DssResult result = decisionEngineService.evaluate(input);

        Assertions.assertEquals(RiskRank.D, result.riskRank());
        Assertions.assertEquals(DssRecommendation.REJECT_RECOMMENDED, result.recommendation());
        Assertions.assertTrue(result.creditScore() < 600);
    }

    @Test
    void shouldKeepBorderlineCasesInApproveLaneForStaffReview() {
        DecisionInput input = new DecisionInput(
                3L,
                BigDecimal.valueOf(18_000_000),
                BigDecimal.valueOf(45),
                "Contract",
                LocalDate.of(1994, 9, 1),
                LocalDate.of(2023, 3, 1),
                55,
                null,
                BigDecimal.valueOf(6_000_000),
                BigDecimal.valueOf(200_000_000),
                24,
                LoanPurpose.PERSONAL,
                null,
                false,
                false,
                false,
                true,
                BigDecimal.valueOf(9_000_000));

        DssResult result = decisionEngineService.evaluate(input);

        Assertions.assertTrue(result.riskRank() == RiskRank.B || result.riskRank() == RiskRank.C);
        Assertions.assertEquals(DssRecommendation.APPROVE_RECOMMENDED, result.recommendation());
    }

    @Test
    void shouldBoostScoreWithPositivePaymentRating() {
        DecisionInput baseInput = new DecisionInput(
                4L,
                BigDecimal.valueOf(40_000_000),
                BigDecimal.valueOf(30),
                "Permanent",
                LocalDate.of(1990, 3, 10),
                LocalDate.of(2018, 6, 1),
                70,
                null,
                BigDecimal.valueOf(5_000_000),
                BigDecimal.valueOf(100_000_000),
                24,
                LoanPurpose.PERSONAL,
                null,
                false,
                false,
                false,
                true,
                BigDecimal.valueOf(5_500_000));
        DecisionInput goodPayerInput = new DecisionInput(
                4L,
                BigDecimal.valueOf(40_000_000),
                BigDecimal.valueOf(30),
                "Permanent",
                LocalDate.of(1990, 3, 10),
                LocalDate.of(2018, 6, 1),
                70,
                null,
                BigDecimal.valueOf(5_000_000),
                BigDecimal.valueOf(100_000_000),
                24,
                LoanPurpose.PERSONAL,
                100,
                false,
                false,
                false,
                true,
                BigDecimal.valueOf(5_500_000));

        DssResult base = decisionEngineService.evaluate(baseInput);
        DssResult boosted = decisionEngineService.evaluate(goodPayerInput);

        Assertions.assertTrue(boosted.creditScore() > base.creditScore());
        Assertions.assertTrue(boosted.creditScore() - base.creditScore() <= 20);
    }

    @Test
    void shouldPenalizeScoreWithNegativePaymentRating() {
        DecisionInput baseInput = new DecisionInput(
                5L,
                BigDecimal.valueOf(40_000_000),
                BigDecimal.valueOf(30),
                "Permanent",
                LocalDate.of(1990, 3, 10),
                LocalDate.of(2018, 6, 1),
                70,
                null,
                BigDecimal.valueOf(5_000_000),
                BigDecimal.valueOf(100_000_000),
                24,
                LoanPurpose.PERSONAL,
                null,
                false,
                false,
                false,
                true,
                BigDecimal.valueOf(5_500_000));
        DecisionInput badPayerInput = new DecisionInput(
                5L,
                BigDecimal.valueOf(40_000_000),
                BigDecimal.valueOf(30),
                "Permanent",
                LocalDate.of(1990, 3, 10),
                LocalDate.of(2018, 6, 1),
                70,
                null,
                BigDecimal.valueOf(5_000_000),
                BigDecimal.valueOf(100_000_000),
                24,
                LoanPurpose.PERSONAL,
                -100,
                false,
                false,
                false,
                true,
                BigDecimal.valueOf(5_500_000));

        DssResult base = decisionEngineService.evaluate(baseInput);
        DssResult penalized = decisionEngineService.evaluate(badPayerInput);

        Assertions.assertTrue(penalized.creditScore() < base.creditScore());
        Assertions.assertTrue(base.creditScore() - penalized.creditScore() <= 20);
    }
}
