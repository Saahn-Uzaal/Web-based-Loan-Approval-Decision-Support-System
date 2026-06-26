package com.loanapproval.dss.loan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loanapproval.dss.creditcheck.CreditBureauStatus;
import com.loanapproval.dss.creditcheck.CustomerCreditCheckService;
import com.loanapproval.dss.creditcheck.CustomerCreditCheckSummary;
import com.loanapproval.dss.debt.CustomerDebtService;
import com.loanapproval.dss.dss.CustomerSegment;
import com.loanapproval.dss.dss.DecisionEngineService;
import com.loanapproval.dss.dss.DecisionInput;
import com.loanapproval.dss.dss.DssRecommendation;
import com.loanapproval.dss.dss.DssResult;
import com.loanapproval.dss.dss.DssResultRecord;
import com.loanapproval.dss.dss.DssResultRepository;
import com.loanapproval.dss.dss.RiskRank;
import com.loanapproval.dss.profile.CustomerProfile;
import com.loanapproval.dss.profile.CustomerProfileRepository;
import com.loanapproval.dss.risk.RiskAssessment;
import com.loanapproval.dss.risk.RiskAssessmentRepository;
import com.loanapproval.dss.risk.RiskAssessmentService;
import com.loanapproval.dss.risk.RiskLevel;
import com.loanapproval.dss.verification.CustomerVerification;
import com.loanapproval.dss.verification.VerificationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoanApprovalReassessmentServiceTest {

    @Mock
    private CustomerProfileRepository customerProfileRepository;

    @Mock
    private CustomerDebtService customerDebtService;

    @Mock
    private DecisionEngineService decisionEngineService;

    @Mock
    private DssResultRepository dssResultRepository;

    @Mock
    private RiskAssessmentRepository riskAssessmentRepository;

    @Mock
    private RiskAssessmentService riskAssessmentService;

    @Mock
    private LoanEligibilityService loanEligibilityService;

    @Mock
    private LoanApplicationSnapshotRepository loanApplicationSnapshotRepository;

    @Mock
    private CustomerCreditCheckService customerCreditCheckService;

    @InjectMocks
    private LoanApprovalReassessmentService loanApprovalReassessmentService;

    @Captor
    private ArgumentCaptor<DecisionInput> decisionInputCaptor;

    @Test
    void shouldApproveUsingStoredAssessmentWithoutReScoring() {
        LoanRecord loan = new LoanRecord(
                301L,
                31L,
                LoanType.UNSECURED,
                BigDecimal.valueOf(300_000_000),
                24,
                LoanPurpose.PERSONAL,
                null,
                null,
                LoanStatus.PENDING,
                null,
                BigDecimal.valueOf(220_000_000),
                BigDecimal.valueOf(200_000_000),
                24,
                BigDecimal.valueOf(0.115000).setScale(6),
                BigDecimal.valueOf(10_500_000),
                "policy-v2",
                "intake",
                Instant.now(),
                Instant.now());
        DssResultRecord storedDss = new DssResultRecord(
                loan.id(),
                713,
                RiskRank.B,
                CustomerSegment.LOW_RISK_HIGH_VALUE,
                DssRecommendation.MANUAL_REVIEW_RECOMMENDED,
                "stored",
                Instant.now());
        RiskAssessment storedRisk = new RiskAssessment(
                loan.id(),
                52,
                20,
                50,
                RiskLevel.MEDIUM,
                "Không có cờ rủi ro cao",
                Instant.now());
        BigDecimal annualRate = BigDecimal.valueOf(0.115000).setScale(6);

        when(dssResultRepository.findByLoanRequestId(loan.id())).thenReturn(Optional.of(storedDss));
        when(riskAssessmentRepository.findByLoanRequestId(loan.id())).thenReturn(Optional.of(storedRisk));
        when(loanEligibilityService.calculateMonthlyPayment(
                        BigDecimal.valueOf(200_000_000),
                        24,
                        annualRate))
                .thenReturn(BigDecimal.valueOf(9_350_000));
        when(loanEligibilityService.currentPolicyVersion()).thenReturn("policy-v2");

        LoanApprovalReassessmentService.ReassessmentResult result =
                loanApprovalReassessmentService.approveUsingStoredAssessment(
                        loan,
                        BigDecimal.valueOf(200_000_000),
                        24,
                        annualRate);

        assertThat(result.approvedAmount()).isEqualByComparingTo("200000000");
        assertThat(result.eligibleLimit()).isEqualByComparingTo("220000000");
        assertThat(result.approvedMonthlyPayment()).isEqualByComparingTo("9350000");
        assertThat(result.explanation()).contains("không chấm lại khi phê duyệt");
        verify(decisionEngineService, never()).evaluate(any());
        verify(riskAssessmentService, never()).evaluate(any(), any(), any(), any());
        verify(dssResultRepository, never()).upsert(any(), any());
        verify(riskAssessmentService, never()).save(any());
    }

    @Test
    void shouldReassessUsingVerifiedIncomeAndAppraisedCollateralValue() {
        LoanRecord loan = new LoanRecord(
                300L,
                30L,
                LoanType.SECURED,
                BigDecimal.valueOf(700_000_000),
                24,
                LoanPurpose.BUSINESS,
                CollateralType.VEHICLE_REGISTRATION,
                BigDecimal.valueOf(900_000_000),
                LoanStatus.APPOINTMENT_SCHEDULED,
                null,
                BigDecimal.valueOf(700_000_000),
                BigDecimal.valueOf(700_000_000),
                24,
                BigDecimal.valueOf(0.120000).setScale(6),
                null,
                LoanEligibilityService.POLICY_VERSION,
                "intake",
                Instant.now(),
                Instant.now());
        CustomerProfile profile = new CustomerProfile(
                loan.customerId(),
                "Trần Thị B",
                "0900000001",
                "012345678901",
                LocalDate.of(1992, 5, 10),
                BigDecimal.valueOf(60_000_000),
                BigDecimal.valueOf(20_000_000),
                BigDecimal.valueOf(25),
                "Permanent",
                LocalDate.of(2018, 3, 1),
                null,
                null,
                85,
                1,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        CustomerVerification verification = fullyVerified(loan.customerId());
        when(loanApplicationSnapshotRepository.findByLoanRequestId(loan.id())).thenReturn(Optional.empty());
        when(customerCreditCheckService.findLatestByCustomerId(loan.customerId()))
                .thenReturn(Optional.of(new CustomerCreditCheckSummary(
                        "012345678901",
                        true,
                        CreditBureauStatus.CLEAR,
                        85,
                        1,
                        0,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        1,
                        false,
                        false,
                        "Clear",
                        "INTERNAL_BUREAU",
                        Instant.parse("2026-01-01T00:00:00Z"))));
        BigDecimal annualRate = BigDecimal.valueOf(0.120000).setScale(6);
        BigDecimal appraisedValue = BigDecimal.valueOf(400_000_000);
        DssResult dssResult = new DssResult(
                700,
                RiskRank.B,
                CustomerSegment.LOW_RISK_LOW_VALUE,
                DssRecommendation.APPROVE_RECOMMENDED,
                "reassessed");
        RiskAssessment riskAssessment = new RiskAssessment(
                loan.id(),
                20,
                10,
                10,
                RiskLevel.LOW,
                "low",
                Instant.now());

        when(customerProfileRepository.findByUserId(loan.customerId())).thenReturn(Optional.of(profile));
        when(customerDebtService.sumActiveMonthlyDebt(loan.customerId())).thenReturn(BigDecimal.valueOf(5_000_000));
        when(loanEligibilityService.calculateMonthlyPayment(
                        BigDecimal.valueOf(700_000_000),
                        24,
                        annualRate))
                .thenReturn(BigDecimal.valueOf(15_000_000));
        when(loanEligibilityService.calculateMonthlyPayment(
                        BigDecimal.valueOf(280_000_000),
                        24,
                        annualRate))
                .thenReturn(BigDecimal.valueOf(6_000_000));
        when(decisionEngineService.evaluate(any(DecisionInput.class))).thenReturn(dssResult);
        when(riskAssessmentService.evaluate(eq(loan.id()), any(DecisionInput.class), eq(dssResult), eq(verification)))
                .thenReturn(riskAssessment);
        when(loanEligibilityService.evaluateWithActualTerms(
                        eq(profile),
                        eq(BigDecimal.valueOf(5_000_000)),
                        eq(LoanType.SECURED),
                        eq(BigDecimal.valueOf(700_000_000)),
                        eq(24),
                        eq(annualRate),
                        eq(appraisedValue),
                        eq(RiskRank.B)))
                .thenReturn(new LoanEligibilityResult(
                        BigDecimal.valueOf(280_000_000),
                        BigDecimal.valueOf(280_000_000),
                        24,
                        annualRate,
                        BigDecimal.valueOf(6_000_000),
                        LoanEligibilityService.POLICY_VERSION,
                        "policy"));
        when(loanEligibilityService.evaluateWithActualTerms(
                        eq(profile),
                        eq(BigDecimal.valueOf(5_000_000)),
                        eq(LoanType.SECURED),
                        eq(BigDecimal.valueOf(280_000_000)),
                        eq(24),
                        eq(annualRate),
                        eq(appraisedValue),
                        eq(RiskRank.B)))
                .thenReturn(new LoanEligibilityResult(
                        BigDecimal.valueOf(280_000_000),
                        BigDecimal.valueOf(280_000_000),
                        24,
                        annualRate,
                        BigDecimal.valueOf(6_000_000),
                        LoanEligibilityService.POLICY_VERSION,
                        "policy"));

        LoanApprovalReassessmentService.ReassessmentResult result =
                loanApprovalReassessmentService.reassessAndPersist(
                        loan,
                        verification,
                        BigDecimal.valueOf(700_000_000),
                        24,
                        annualRate,
                        appraisedValue,
                        true);

        verify(decisionEngineService, org.mockito.Mockito.times(2)).evaluate(decisionInputCaptor.capture());
        DecisionInput firstPass = decisionInputCaptor.getAllValues().get(0);
        assertThat(firstPass.monthlyIncome()).isEqualByComparingTo("20000000");
        assertThat(firstPass.collateralValue()).isEqualByComparingTo("400000000");
        assertThat(firstPass.debtToIncomeRatio()).isEqualByComparingTo("100.00");
        assertThat(firstPass.requestedAmount()).isEqualByComparingTo("700000000");
        assertThat(result.approvedAmount()).isEqualByComparingTo("280000000");
        assertThat(result.eligibleLimit()).isEqualByComparingTo("280000000");
        assertThat(result.amountAdjusted()).isTrue();
        assertThat(result.explanation()).contains("giá trị tài sản sau thẩm định=400000000");
    }

    private CustomerVerification fullyVerified(Long customerId) {
        Instant now = Instant.now();
        return new CustomerVerification(
                customerId,
                VerificationStatus.PASSED,
                VerificationStatus.PASSED,
                VerificationStatus.PASSED,
                VerificationStatus.PASSED,
                VerificationStatus.PASSED,
                VerificationStatus.PASSED,
                false,
                null,
                11L,
                now,
                now,
                now);
    }
}
