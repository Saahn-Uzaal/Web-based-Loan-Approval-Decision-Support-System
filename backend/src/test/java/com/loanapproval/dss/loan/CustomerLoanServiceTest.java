package com.loanapproval.dss.loan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loanapproval.dss.compliance.ComplianceAuditService;
import com.loanapproval.dss.contract.LoanContract;
import com.loanapproval.dss.contract.LoanContractService;
import com.loanapproval.dss.contract.LoanContractStatus;
import com.loanapproval.dss.creditcheck.CreditBureauStatus;
import com.loanapproval.dss.creditcheck.CustomerCreditCheckService;
import com.loanapproval.dss.creditcheck.CustomerCreditCheckSummary;
import com.loanapproval.dss.customerinfo.CustomerInformationVerificationService;
import com.loanapproval.dss.debt.CustomerDebtService;
import com.loanapproval.dss.dss.CustomerSegment;
import com.loanapproval.dss.dss.DecisionEngineService;
import com.loanapproval.dss.dss.DecisionInput;
import com.loanapproval.dss.dss.DssRecommendation;
import com.loanapproval.dss.dss.DssResult;
import com.loanapproval.dss.dss.DssResultRepository;
import com.loanapproval.dss.dss.RiskRank;
import com.loanapproval.dss.loan.dto.AcceptApprovedLoanRequest;
import com.loanapproval.dss.loan.dto.CreateLoanRequest;
import com.loanapproval.dss.loan.dto.LoanDetailResponse;
import com.loanapproval.dss.notification.NotificationService;
import com.loanapproval.dss.profile.CustomerProfile;
import com.loanapproval.dss.profile.CustomerProfileRepository;
import com.loanapproval.dss.repayment.RepaymentScheduleService;
import com.loanapproval.dss.risk.RiskAssessment;
import com.loanapproval.dss.risk.RiskAssessmentService;
import com.loanapproval.dss.risk.RiskLevel;
import com.loanapproval.dss.verification.CustomerVerification;
import com.loanapproval.dss.verification.CustomerVerificationService;
import com.loanapproval.dss.verification.VerificationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class CustomerLoanServiceTest {

    @Mock
    private LoanRepository loanRepository;

    @Mock
    private LoanDocumentRepository loanDocumentRepository;

    @Mock
    private LoanDocumentStorageService loanDocumentStorageService;

    @Mock
    private CustomerProfileRepository customerProfileRepository;

    @Mock
    private CustomerDebtService customerDebtService;

    @Mock
    private DecisionEngineService decisionEngineService;

    @Mock
    private DssResultRepository dssResultRepository;

    @Mock
    private CustomerCreditCheckService customerCreditCheckService;

    @Mock
    private CustomerVerificationService customerVerificationService;

    @Mock
    private RiskAssessmentService riskAssessmentService;

    @Mock
    private ComplianceAuditService complianceAuditService;

    @Mock
    private LoanContractService loanContractService;

    @Mock
    private LoanContractAcceptanceCaptchaService loanContractAcceptanceCaptchaService;

    @Mock
    private CustomerInformationVerificationService customerInformationVerificationService;

    @Mock
    private LoanEligibilityService loanEligibilityService;

    @Mock
    private LoanSlaService loanSlaService;

    @Mock
    private RepaymentScheduleService repaymentScheduleService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private LoanApplicationSnapshotRepository loanApplicationSnapshotRepository;

    @Mock
    private LoanStatusHistoryService loanStatusHistoryService;

    @Mock
    private LoanAppointmentRepository loanAppointmentRepository;

    @InjectMocks
    private CustomerLoanService customerLoanService;

    @Captor
    private ArgumentCaptor<DecisionInput> decisionInputCaptor;

    @Test
    void shouldUseVerifiedIncomeWhenPreparingDssInputForLoanCreation() {
        Long customerId = 10L;
        CreateLoanRequest request = new CreateLoanRequest(
                LoanType.UNSECURED,
                BigDecimal.valueOf(100_000_000),
                24,
                LoanPurpose.PERSONAL,
                null,
                null);
        CustomerProfile profile = profile(BigDecimal.valueOf(50_000_000), BigDecimal.valueOf(20_000_000));
        LoanRecord createdLoan = loanRecord(100L, customerId, LoanType.UNSECURED, LoanStatus.PENDING);
        DssResult dssResult = new DssResult(
                720,
                RiskRank.A,
                CustomerSegment.LOW_RISK_LOW_VALUE,
                DssRecommendation.APPROVE_RECOMMENDED,
                "eligible");
        LoanEligibilityResult eligibility = new LoanEligibilityResult(
                BigDecimal.valueOf(180_000_000),
                BigDecimal.valueOf(100_000_000),
                24,
                BigDecimal.valueOf(0.120000).setScale(6),
                BigDecimal.valueOf(5_000_000),
                LoanEligibilityService.POLICY_VERSION,
                "policy");

        when(customerProfileRepository.findByUserId(customerId)).thenReturn(Optional.of(profile));
        when(customerDebtService.sumActiveMonthlyDebt(customerId)).thenReturn(BigDecimal.valueOf(5_000_000));
        when(loanContractService.calculateProjectedMonthlyPayment(LoanType.UNSECURED, request.amount(), request.termMonths()))
                .thenReturn(BigDecimal.valueOf(5_000_000));
        when(customerVerificationService.getOrDefault(customerId)).thenReturn(fullyVerified(customerId));
        when(customerCreditCheckService.findLatestByCustomerId(customerId))
                .thenReturn(Optional.of(creditCheck("012345678901", 74, false, false)));
        when(decisionEngineService.evaluate(any(DecisionInput.class))).thenReturn(dssResult);
        when(loanEligibilityService.evaluate(
                        eq(profile),
                        eq(BigDecimal.valueOf(5_000_000)),
                        eq(LoanType.UNSECURED),
                        eq(request.amount()),
                        eq(request.termMonths()),
                        eq(request.collateralValue()),
                        eq(dssResult.riskRank())))
                .thenReturn(eligibility);
        when(loanRepository.create(anyLong(), any(), any(), anyInt(), any(), any(), any(), anyString()))
                .thenReturn(createdLoan);
        when(riskAssessmentService.evaluateAndSave(anyLong(), any(), eq(dssResult), any()))
                .thenReturn(riskAssessment(createdLoan.id(), RiskLevel.MEDIUM));
        when(loanRepository.findOwnedById(createdLoan.id(), customerId)).thenReturn(Optional.of(createdLoan));
        when(loanDocumentRepository.findByLoanRequestId(createdLoan.id())).thenReturn(List.of());

        LoanDetailResponse response = customerLoanService.create(customerId, request);

        verify(decisionEngineService).evaluate(decisionInputCaptor.capture());
        DecisionInput capturedInput = decisionInputCaptor.getValue();
        assertThat(capturedInput.monthlyIncome()).isEqualByComparingTo("20000000");
        assertThat(capturedInput.debtToIncomeRatio()).isEqualByComparingTo("50.00");
        assertThat(capturedInput.creditHistoryScore()).isEqualTo(74);
        assertThat(response.status()).isEqualTo(LoanStatus.PENDING);
        verify(notificationService).notifyStaffLoanApplicationSubmitted(createdLoan.id(), customerId, LoanType.UNSECURED);
        verify(loanRepository, never()).updateCollateralValue(anyLong(), any());
    }

    @Test
    void shouldKeepSecuredLowRiskLoanPendingInsteadOfAutoApproving() {
        Long customerId = 20L;
        CreateLoanRequest request = new CreateLoanRequest(
                LoanType.SECURED,
                BigDecimal.valueOf(300_000_000),
                36,
                LoanPurpose.BUSINESS,
                CollateralType.VEHICLE_REGISTRATION,
                BigDecimal.valueOf(600_000_000));
        CustomerProfile profile = profile(BigDecimal.valueOf(45_000_000), BigDecimal.valueOf(45_000_000));
        LoanRecord createdLoan = loanRecord(200L, customerId, LoanType.SECURED, LoanStatus.PENDING);
        DssResult dssResult = new DssResult(
                760,
                RiskRank.A,
                CustomerSegment.LOW_RISK_LOW_VALUE,
                DssRecommendation.APPROVE_RECOMMENDED,
                "low risk");
        LoanEligibilityResult eligibility = new LoanEligibilityResult(
                BigDecimal.valueOf(420_000_000),
                BigDecimal.valueOf(300_000_000),
                36,
                BigDecimal.valueOf(0.105000).setScale(6),
                BigDecimal.valueOf(9_500_000),
                LoanEligibilityService.POLICY_VERSION,
                "policy");

        when(customerProfileRepository.findByUserId(customerId)).thenReturn(Optional.of(profile));
        when(customerDebtService.sumActiveMonthlyDebt(customerId)).thenReturn(BigDecimal.valueOf(3_000_000));
        when(loanContractService.calculateProjectedMonthlyPayment(LoanType.SECURED, request.amount(), request.termMonths()))
                .thenReturn(BigDecimal.valueOf(9_500_000));
        when(customerVerificationService.getOrDefault(customerId)).thenReturn(fullyVerified(customerId));
        when(customerCreditCheckService.findLatestByCustomerId(customerId))
                .thenReturn(Optional.of(creditCheck("012345678901", 82, false, false)));
        when(decisionEngineService.evaluate(any(DecisionInput.class))).thenReturn(dssResult);
        when(loanEligibilityService.evaluate(
                        eq(profile),
                        eq(BigDecimal.valueOf(3_000_000)),
                        eq(LoanType.SECURED),
                        eq(request.amount()),
                        eq(request.termMonths()),
                        eq(request.collateralValue()),
                        eq(dssResult.riskRank())))
                .thenReturn(eligibility);
        when(loanRepository.create(anyLong(), any(), any(), anyInt(), any(), any(), any(), anyString()))
                .thenReturn(createdLoan);
        when(riskAssessmentService.evaluateAndSave(anyLong(), any(), eq(dssResult), any()))
                .thenReturn(riskAssessment(createdLoan.id(), RiskLevel.LOW));
        when(loanRepository.findOwnedById(createdLoan.id(), customerId)).thenReturn(Optional.of(createdLoan));
        when(loanDocumentRepository.findByLoanRequestId(createdLoan.id())).thenReturn(List.of());

        LoanDetailResponse response = customerLoanService.create(customerId, request);

        assertThat(response.status()).isEqualTo(LoanStatus.PENDING);
        verify(loanRepository).updateCollateralValue(createdLoan.id(), request.collateralValue());
        verify(loanRepository, never())
                .updateDecision(
                        eq(createdLoan.id()),
                        eq(LoanStatus.APPROVED),
                        anyString(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        anyString());
    }

    @Test
    void shouldUseLowCreditScoreReasonWhenSecuredLoanIsAutoRejected() {
        Long customerId = 30L;
        CreateLoanRequest request = new CreateLoanRequest(
                LoanType.SECURED,
                BigDecimal.valueOf(250_000_000),
                24,
                LoanPurpose.BUSINESS,
                CollateralType.VEHICLE_REGISTRATION,
                BigDecimal.valueOf(500_000_000));
        CustomerProfile profile = profile(BigDecimal.valueOf(30_000_000), BigDecimal.valueOf(30_000_000));
        LoanRecord createdLoan = loanRecord(300L, customerId, LoanType.SECURED, LoanStatus.PENDING);
        DssResult dssResult = new DssResult(
                580,
                RiskRank.D,
                CustomerSegment.HIGH_RISK_LOW_VALUE,
                DssRecommendation.REJECT_RECOMMENDED,
                "rejected");
        LoanEligibilityResult eligibility = new LoanEligibilityResult(
                BigDecimal.valueOf(350_000_000),
                BigDecimal.valueOf(250_000_000),
                24,
                BigDecimal.valueOf(0.120000).setScale(6),
                BigDecimal.valueOf(11_000_000),
                LoanEligibilityService.POLICY_VERSION,
                "policy");

        when(customerProfileRepository.findByUserId(customerId)).thenReturn(Optional.of(profile));
        when(customerDebtService.sumActiveMonthlyDebt(customerId)).thenReturn(BigDecimal.valueOf(2_000_000));
        when(loanContractService.calculateProjectedMonthlyPayment(LoanType.SECURED, request.amount(), request.termMonths()))
                .thenReturn(BigDecimal.valueOf(11_000_000));
        when(customerVerificationService.getOrDefault(customerId)).thenReturn(fullyVerified(customerId));
        when(customerCreditCheckService.findLatestByCustomerId(customerId))
                .thenReturn(Optional.of(creditCheck("012345678901", 58, false, false)));
        when(decisionEngineService.evaluate(any(DecisionInput.class))).thenReturn(dssResult);
        when(loanEligibilityService.evaluate(
                        eq(profile),
                        eq(BigDecimal.valueOf(2_000_000)),
                        eq(LoanType.SECURED),
                        eq(request.amount()),
                        eq(request.termMonths()),
                        eq(request.collateralValue()),
                        eq(dssResult.riskRank())))
                .thenReturn(eligibility);
        when(loanRepository.create(anyLong(), any(), any(), anyInt(), any(), any(), any(), anyString()))
                .thenReturn(createdLoan);
        when(riskAssessmentService.evaluateAndSave(anyLong(), any(), eq(dssResult), any()))
                .thenReturn(riskAssessment(createdLoan.id(), RiskLevel.HIGH));
        when(loanRepository.findOwnedById(createdLoan.id(), customerId)).thenReturn(Optional.of(createdLoan));
        when(loanDocumentRepository.findByLoanRequestId(createdLoan.id())).thenReturn(List.of());

        customerLoanService.create(customerId, request);

        verify(loanRepository).updateCollateralValue(createdLoan.id(), request.collateralValue());
        verify(loanRepository, times(1))
                .updateDecision(
                        eq(createdLoan.id()),
                        eq(LoanStatus.REJECTED),
                        eq("Hồ sơ vay thế chấp đã bị tự động từ chối vì điểm tín dụng quá thấp nên hồ sơ đã bị hủy."),
                        eq(null),
                        eq(null),
                        eq(null),
                        eq(null),
                        eq(null));
        verify(notificationService)
                .notifyCustomerLoanDecisionUpdated(
                        eq(createdLoan.id()),
                        eq(customerId),
                        eq(null),
                        eq(LoanType.SECURED),
                        eq(LoanStatus.REJECTED),
                        eq("Hồ sơ vay thế chấp đã bị tự động từ chối vì điểm tín dụng quá thấp nên hồ sơ đã bị hủy."),
                        eq(true),
                        eq(null));
    }

    @Test
    void shouldRejectCreatingNewLoanWhenCustomerAlreadyHasOpenApplication() {
        Long customerId = 40L;
        CreateLoanRequest request = new CreateLoanRequest(
                LoanType.UNSECURED,
                BigDecimal.valueOf(80_000_000),
                18,
                LoanPurpose.PERSONAL,
                null,
                null);
        when(loanRepository.existsOpenApplicationByCustomerId(customerId)).thenReturn(true);

        ResponseStatusException exception = Assertions.assertThrows(
                ResponseStatusException.class,
                () -> customerLoanService.create(customerId, request));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.getReason()).contains("1 hồ sơ vay");
    }

    @Test
    void shouldCreateDraftWithoutRequiringLoanDocuments() {
        Long customerId = 45L;
        CreateLoanRequest request = new CreateLoanRequest(
                LoanType.UNSECURED,
                BigDecimal.valueOf(60_000_000),
                12,
                LoanPurpose.PERSONAL,
                null,
                null);
        LoanRecord draftLoan = loanRecord(450L, customerId, LoanType.UNSECURED, LoanStatus.DRAFT);

        when(loanRepository.existsOpenApplicationByCustomerId(customerId)).thenReturn(false);
        when(loanRepository.create(
                        customerId,
                        LoanType.UNSECURED,
                        request.amount(),
                        request.termMonths(),
                        request.purpose(),
                        null,
                        LoanStatus.DRAFT,
                        null,
                        "Bản nháp hồ sơ vay đã được lưu. Bạn có thể quay lại bổ sung chứng từ rồi gửi thẩm định sau."))
                .thenReturn(draftLoan);
        when(loanRepository.findOwnedById(draftLoan.id(), customerId)).thenReturn(Optional.of(draftLoan));
        when(loanDocumentRepository.findByLoanRequestId(draftLoan.id())).thenReturn(List.of());

        LoanDetailResponse response = customerLoanService.createDraft(customerId, request);

        assertThat(response.status()).isEqualTo(LoanStatus.DRAFT);
        verify(notificationService, never()).notifyStaffLoanApplicationSubmitted(anyLong(), anyLong(), any());
    }

    @Test
    void shouldRequireSupplementalDocumentsWhenResubmittingLoan() {
        Long customerId = 50L;
        LoanRecord loan = loanRecord(500L, customerId, LoanType.SECURED, LoanStatus.NEEDS_MORE_INFO);
        when(loanRepository.findOwnedById(loan.id(), customerId)).thenReturn(Optional.of(loan));

        ResponseStatusException exception = Assertions.assertThrows(
                ResponseStatusException.class,
                () -> customerLoanService.resubmitLoan(customerId, loan.id()));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getReason()).contains("ít nhất một giấy tờ bổ sung");
    }

    @Test
    void shouldSubmitDraftUsingExistingDocuments() {
        Long customerId = 55L;
        CreateLoanRequest request = new CreateLoanRequest(
                LoanType.UNSECURED,
                BigDecimal.valueOf(100_000_000),
                24,
                LoanPurpose.PERSONAL,
                null,
                null);
        CustomerProfile profile = profile(BigDecimal.valueOf(50_000_000), BigDecimal.valueOf(20_000_000));
        LoanRecord draftLoan = loanRecord(550L, customerId, LoanType.UNSECURED, LoanStatus.DRAFT);
        LoanRecord pendingLoan = loanRecord(550L, customerId, LoanType.UNSECURED, LoanStatus.PENDING);
        DssResult dssResult = new DssResult(
                720,
                RiskRank.A,
                CustomerSegment.LOW_RISK_LOW_VALUE,
                DssRecommendation.APPROVE_RECOMMENDED,
                "eligible");
        LoanEligibilityResult eligibility = new LoanEligibilityResult(
                BigDecimal.valueOf(180_000_000),
                BigDecimal.valueOf(100_000_000),
                24,
                BigDecimal.valueOf(0.120000).setScale(6),
                BigDecimal.valueOf(5_000_000),
                LoanEligibilityService.POLICY_VERSION,
                "policy");

        when(customerProfileRepository.findByUserId(customerId)).thenReturn(Optional.of(profile));
        when(customerDebtService.sumActiveMonthlyDebt(customerId)).thenReturn(BigDecimal.valueOf(5_000_000));
        when(loanContractService.calculateProjectedMonthlyPayment(LoanType.UNSECURED, request.amount(), request.termMonths()))
                .thenReturn(BigDecimal.valueOf(5_000_000));
        when(customerVerificationService.getOrDefault(customerId)).thenReturn(fullyVerified(customerId));
        when(customerCreditCheckService.findLatestByCustomerId(customerId))
                .thenReturn(Optional.of(creditCheck("012345678901", 74, false, false)));
        when(decisionEngineService.evaluate(any(DecisionInput.class))).thenReturn(dssResult);
        when(loanEligibilityService.evaluate(
                        eq(profile),
                        eq(BigDecimal.valueOf(5_000_000)),
                        eq(LoanType.UNSECURED),
                        eq(request.amount()),
                        eq(request.termMonths()),
                        eq(request.collateralValue()),
                        eq(dssResult.riskRank())))
                .thenReturn(eligibility);
        when(loanRepository.findOwnedById(draftLoan.id(), customerId))
                .thenReturn(Optional.of(draftLoan), Optional.of(pendingLoan), Optional.of(pendingLoan));
        when(loanRepository.submitOwnedDraftForReview(
                        draftLoan.id(),
                        customerId,
                        LoanType.UNSECURED,
                        request.amount(),
                        request.termMonths(),
                        request.purpose(),
                        null,
                        eligibility.eligibleLimit(),
                        "Hồ sơ vay tín chấp đã được gửi thẩm định. Nhân viên sẽ dùng CCCD đã lưu trong hồ sơ gốc, ảnh khuôn mặt hiện tại, thông tin tín dụng nội bộ và DTI để đối chiếu."))
                .thenReturn(1);
        when(loanDocumentRepository.findByLoanRequestId(draftLoan.id()))
                .thenReturn(List.of(
                        loanDocument(1L, draftLoan.id(), LoanDocumentType.ID_CARD_FRONT),
                        loanDocument(2L, draftLoan.id(), LoanDocumentType.ID_CARD_BACK),
                        loanDocument(3L, draftLoan.id(), LoanDocumentType.FACE_CAPTURE)));
        when(riskAssessmentService.evaluateAndSave(anyLong(), any(), eq(dssResult), any()))
                .thenReturn(riskAssessment(draftLoan.id(), RiskLevel.MEDIUM));

        LoanDetailResponse response = customerLoanService.submitDraft(customerId, draftLoan.id(), request);

        assertThat(response.status()).isEqualTo(LoanStatus.PENDING);
        verify(notificationService).notifyStaffLoanApplicationSubmitted(draftLoan.id(), customerId, LoanType.UNSECURED);
        verify(loanStatusHistoryService).recordTransition(
                eq(draftLoan),
                eq(LoanStatus.PENDING),
                eq(customerId),
                eq("CUSTOMER_SUBMIT_DRAFT"),
                eq("Customer submitted draft loan application for review"));
    }

    @Test
    void shouldNotifyAssignedStaffWhenCustomerAcceptsContract() {
        Long customerId = 60L;
        Long loanRequestId = 600L;
        Long assignedStaffUserId = 9L;
        LoanRecord approvedLoan = loanRecord(loanRequestId, customerId, LoanType.UNSECURED, LoanStatus.APPROVED);
        LoanRecord contractedLoan = loanRecord(loanRequestId, customerId, LoanType.UNSECURED, LoanStatus.CONTRACTED);

        when(loanRepository.findOwnedById(loanRequestId, customerId))
                .thenReturn(Optional.of(approvedLoan), Optional.of(contractedLoan));
        when(loanRepository.findAssignedStaffUserId(loanRequestId)).thenReturn(Optional.of(assignedStaffUserId));
        when(loanContractService.activateForCustomer(customerId, loanRequestId)).thenReturn(activeContract(loanRequestId, customerId));
        when(loanRepository.markAcceptedAndContracted(eq(loanRequestId), eq(customerId), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(1);
        when(loanDocumentRepository.findByLoanRequestId(loanRequestId)).thenReturn(List.of());

        LoanDetailResponse response = customerLoanService.acceptApprovedLoan(
                customerId,
                loanRequestId,
                new AcceptApprovedLoanRequest(true, "captcha-token", 15));

        assertThat(response.status()).isEqualTo(LoanStatus.CONTRACTED);
        verify(loanContractAcceptanceCaptchaService).validateChallenge(customerId, loanRequestId, "captcha-token", 15);
        verify(notificationService).notifyStaffLoanContractAccepted(
                loanRequestId,
                customerId,
                assignedStaffUserId,
                LoanType.UNSECURED);
    }

    @Test
    void shouldNotifyAssignedStaffWhenCustomerWithdrawsLoan() {
        Long customerId = 70L;
        Long loanRequestId = 700L;
        Long assignedStaffUserId = 11L;
        LoanRecord pendingLoan = loanRecord(loanRequestId, customerId, LoanType.SECURED, LoanStatus.PENDING);
        LoanRecord withdrawnLoan = loanRecord(loanRequestId, customerId, LoanType.SECURED, LoanStatus.WITHDRAWN);

        when(loanRepository.findOwnedById(loanRequestId, customerId))
                .thenReturn(Optional.of(pendingLoan), Optional.of(withdrawnLoan));
        when(loanRepository.findAssignedStaffUserId(loanRequestId)).thenReturn(Optional.of(assignedStaffUserId));
        when(loanRepository.withdrawOwnedApplication(
                        loanRequestId,
                        customerId,
                        "Khách hàng đã rút hồ sơ trước khi ký hợp đồng"))
                .thenReturn(1);
        when(loanDocumentRepository.findByLoanRequestId(loanRequestId)).thenReturn(List.of());

        LoanDetailResponse response = customerLoanService.withdrawLoan(customerId, loanRequestId);

        assertThat(response.status()).isEqualTo(LoanStatus.WITHDRAWN);
        verify(loanContractService).cancelPendingAcceptance(loanRequestId);
        verify(notificationService).notifyStaffLoanWithdrawn(
                loanRequestId,
                customerId,
                assignedStaffUserId,
                LoanType.SECURED);
    }

    @Test
    void shouldRejectWithdrawingApprovedLoan() {
        Long customerId = 71L;
        Long loanRequestId = 701L;
        LoanRecord approvedLoan = loanRecord(loanRequestId, customerId, LoanType.UNSECURED, LoanStatus.APPROVED);

        when(loanRepository.findOwnedById(loanRequestId, customerId)).thenReturn(Optional.of(approvedLoan));

        ResponseStatusException exception = Assertions.assertThrows(
                ResponseStatusException.class,
                () -> customerLoanService.withdrawLoan(customerId, loanRequestId));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.getReason()).contains("bản nháp, chờ thẩm định hoặc đang chờ bổ sung");
        verify(loanRepository, never()).withdrawOwnedApplication(anyLong(), anyLong(), anyString());
        verify(loanContractService, never()).cancelPendingAcceptance(anyLong());
    }

    private CustomerProfile profile(BigDecimal monthlyIncome, BigDecimal verifiedMonthlyIncome) {
        Instant uploadedAt = Instant.parse("2026-01-01T00:00:00Z");
        return new CustomerProfile(
                1L,
                "Nguyễn Văn A",
                "0900000000",
                "012345678901",
                LocalDate.of(1990, 1, 1),
                monthlyIncome,
                verifiedMonthlyIncome,
                BigDecimal.valueOf(20),
                "Permanent",
                LocalDate.of(2015, 1, 1),
                "19036866889922",
                "Vietcombank",
                90,
                0,
                "payslip.pdf",
                "stored-payslip.pdf",
                "application/pdf",
                1024L,
                uploadedAt,
                "id-front.jpg",
                "stored-front.jpg",
                "image/jpeg",
                2048L,
                uploadedAt,
                "id-back.jpg",
                "stored-back.jpg",
                "image/jpeg",
                2048L,
                uploadedAt);
    }

    private CustomerCreditCheckSummary creditCheck(
            String identityNumber,
            Integer creditScore,
            boolean manualReviewRequired,
            boolean hardReject) {
        return new CustomerCreditCheckSummary(
                identityNumber,
                true,
                manualReviewRequired ? CreditBureauStatus.WATCHLIST : CreditBureauStatus.CLEAR,
                creditScore,
                1,
                0,
                manualReviewRequired,
                hardReject,
                manualReviewRequired ? "Manual review required" : "Clear",
                "INTERNAL_BUREAU",
                Instant.parse("2026-01-01T00:00:00Z"));
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
                99L,
                now,
                now,
                now);
    }

    private LoanRecord loanRecord(Long id, Long customerId, LoanType loanType, LoanStatus status) {
        Instant now = Instant.now();
        return new LoanRecord(
                id,
                customerId,
                loanType,
                BigDecimal.valueOf(100_000_000),
                24,
                LoanPurpose.PERSONAL,
                loanType == LoanType.SECURED ? CollateralType.VEHICLE_REGISTRATION : null,
                loanType == LoanType.SECURED ? BigDecimal.valueOf(150_000_000) : null,
                status,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "intake",
                now,
                now);
    }

    private LoanContract activeContract(Long loanRequestId, Long customerId) {
        Instant now = Instant.now();
        return new LoanContract(
                77L,
                loanRequestId,
                customerId,
                BigDecimal.valueOf(100_000_000),
                BigDecimal.valueOf(0.12).setScale(6),
                24,
                LocalDate.now(),
                LocalDate.now().plusMonths(24),
                LocalDate.now().plusMonths(1),
                "15",
                LocalDate.now().plusMonths(24),
                BigDecimal.valueOf(4_800_000),
                BigDecimal.valueOf(15_200_000),
                LoanContractStatus.ACTIVE,
                now,
                now);
    }

    private RiskAssessment riskAssessment(Long loanRequestId, RiskLevel riskLevel) {
        return new RiskAssessment(loanRequestId, 20, 10, 10, riskLevel, "risk", Instant.now());
    }

    private LoanDocumentRecord loanDocument(Long id, Long loanRequestId, LoanDocumentType documentType) {
        return new LoanDocumentRecord(
                id,
                loanRequestId,
                documentType,
                documentType.name().toLowerCase(),
                documentType.name().toLowerCase() + ".jpg",
                "image/jpeg",
                2048L,
                Instant.now());
    }
}
