package com.loanapproval.dss.loan;

import com.loanapproval.dss.dss.RiskRank;
import com.loanapproval.dss.profile.CustomerProfile;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class LoanEligibilityServiceTest {

    private final LoanEligibilityService service = new LoanEligibilityService();

    @Test
    void shouldApplyVndProductCapAndRiskAdjustmentForUnsecuredLoan() {
        LoanEligibilityResult result = service.evaluate(
                profile(BigDecimal.valueOf(50_000_000)),
                BigDecimal.valueOf(5_000_000),
                LoanType.UNSECURED,
                BigDecimal.valueOf(600_000_000),
                24,
                null,
                RiskRank.B);

        Assertions.assertEquals(BigDecimal.valueOf(255_000_000), result.eligibleLimit());
        Assertions.assertEquals(BigDecimal.valueOf(255_000_000), result.approvedAmount());
        Assertions.assertEquals(BigDecimal.valueOf(0.120000).setScale(6), result.approvedAnnualRate());
        Assertions.assertEquals(LoanEligibilityService.POLICY_VERSION, result.decisionPolicyVersion());
        Assertions.assertTrue(result.approvedMonthlyPayment().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void shouldUseCollateralLtvForSecuredLoan() {
        LoanEligibilityResult result = service.evaluate(
                profile(BigDecimal.valueOf(100_000_000)),
                BigDecimal.ZERO,
                LoanType.SECURED,
                BigDecimal.valueOf(700_000_000),
                36,
                BigDecimal.valueOf(800_000_000),
                RiskRank.A);

        Assertions.assertEquals(BigDecimal.valueOf(560_000_000), result.eligibleLimit());
        Assertions.assertEquals(BigDecimal.valueOf(560_000_000), result.approvedAmount());
        Assertions.assertEquals(BigDecimal.valueOf(0.105000).setScale(6), result.approvedAnnualRate());
    }

    @Test
    void shouldReturnZeroApprovalForRankD() {
        LoanEligibilityResult result = service.evaluate(
                profile(BigDecimal.valueOf(80_000_000)),
                BigDecimal.ZERO,
                LoanType.UNSECURED,
                BigDecimal.valueOf(100_000_000),
                12,
                null,
                RiskRank.D);

        Assertions.assertEquals(BigDecimal.ZERO.setScale(0), result.eligibleLimit());
        Assertions.assertEquals(BigDecimal.ZERO.setScale(0), result.approvedAmount());
        Assertions.assertEquals(BigDecimal.ZERO.setScale(0), result.approvedMonthlyPayment());
    }

    private CustomerProfile profile(BigDecimal monthlyIncome) {
        return new CustomerProfile(
                1L,
                "Nguyen Van A",
                "0900000000",
                LocalDate.of(1990, 1, 1),
                monthlyIncome,
                null,
                BigDecimal.valueOf(20),
                "Permanent",
                LocalDate.of(2015, 1, 1),
                90,
                0,
                null,
                null,
                null,
                null,
                null);
    }
}
