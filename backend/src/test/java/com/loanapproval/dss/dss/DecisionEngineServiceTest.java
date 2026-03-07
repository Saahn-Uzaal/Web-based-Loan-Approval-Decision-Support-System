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
            BigDecimal.valueOf(6000),
            BigDecimal.valueOf(18),
            "Permanent",
            LocalDate.of(1992, 5, 20),
            LocalDate.of(2016, 1, 1),
            85,
            BigDecimal.valueOf(25000),
            BigDecimal.valueOf(800),
            BigDecimal.valueOf(12000),
            18,
            LoanPurpose.HOME,
            null,
            false,
            false,
            false,
            true
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
            LocalDate.of(2004, 8, 12),
            null,
            20,
            null,
            BigDecimal.valueOf(300),
            BigDecimal.valueOf(25000),
            36,
            LoanPurpose.BUSINESS,
            null,
            false,
            false,
            false,
            false
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
            LocalDate.of(1994, 9, 1),
            LocalDate.of(2023, 3, 1),
            55,
            null,
            BigDecimal.valueOf(600),
            BigDecimal.valueOf(15000),
            24,
            LoanPurpose.PERSONAL,
            null,
            false,
            false,
            false,
            true
        );

        DssResult result = decisionEngineService.evaluate(input);

        Assertions.assertTrue(result.riskRank() == RiskRank.B || result.riskRank() == RiskRank.C);
        Assertions.assertEquals(DssRecommendation.ESCALATE_RECOMMENDED, result.recommendation());
    }

    @Test
    void shouldBoostScoreWithPositivePaymentRating() {
        DecisionInput baseInput = new DecisionInput(
            4L,
            BigDecimal.valueOf(4000),
            BigDecimal.valueOf(30),
            "Permanent",
            LocalDate.of(1990, 3, 10),
            LocalDate.of(2018, 6, 1),
            70,
            null,
            BigDecimal.valueOf(500),
            BigDecimal.valueOf(10000),
            24,
            LoanPurpose.PERSONAL,
            null,
            false,
            false,
            false,
            true
        );
        DecisionInput goodPayerInput = new DecisionInput(
            4L,
            BigDecimal.valueOf(4000),
            BigDecimal.valueOf(30),
            "Permanent",
            LocalDate.of(1990, 3, 10),
            LocalDate.of(2018, 6, 1),
            70,
            null,
            BigDecimal.valueOf(500),
            BigDecimal.valueOf(10000),
            24,
            LoanPurpose.PERSONAL,
            100,
            false,
            false,
            false,
            true
        );

        DssResult base = decisionEngineService.evaluate(baseInput);
        DssResult boosted = decisionEngineService.evaluate(goodPayerInput);

        Assertions.assertTrue(boosted.creditScore() > base.creditScore(),
            "Good payment history should increase credit score");
        Assertions.assertTrue(boosted.creditScore() - base.creditScore() <= 20,
            "Payment bonus should not exceed 20 points");
    }

    @Test
    void shouldPenalizeScoreWithNegativePaymentRating() {
        DecisionInput baseInput = new DecisionInput(
            5L,
            BigDecimal.valueOf(4000),
            BigDecimal.valueOf(30),
            "Permanent",
            LocalDate.of(1990, 3, 10),
            LocalDate.of(2018, 6, 1),
            70,
            null,
            BigDecimal.valueOf(500),
            BigDecimal.valueOf(10000),
            24,
            LoanPurpose.PERSONAL,
            null,
            false,
            false,
            false,
            true
        );
        DecisionInput badPayerInput = new DecisionInput(
            5L,
            BigDecimal.valueOf(4000),
            BigDecimal.valueOf(30),
            "Permanent",
            LocalDate.of(1990, 3, 10),
            LocalDate.of(2018, 6, 1),
            70,
            null,
            BigDecimal.valueOf(500),
            BigDecimal.valueOf(10000),
            24,
            LoanPurpose.PERSONAL,
            -100,
            false,
            false,
            false,
            true
        );

        DssResult base = decisionEngineService.evaluate(baseInput);
        DssResult penalized = decisionEngineService.evaluate(badPayerInput);

        Assertions.assertTrue(penalized.creditScore() < base.creditScore(),
            "Bad payment history should decrease credit score");
        Assertions.assertTrue(base.creditScore() - penalized.creditScore() <= 20,
            "Payment penalty should not exceed 20 points");
    }
}
