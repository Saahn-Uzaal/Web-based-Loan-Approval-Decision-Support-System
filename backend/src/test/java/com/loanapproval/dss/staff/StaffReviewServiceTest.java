package com.loanapproval.dss.staff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loanapproval.dss.compliance.ComplianceAuditService;
import com.loanapproval.dss.contract.LoanContract;
import com.loanapproval.dss.contract.LoanContractService;
import com.loanapproval.dss.contract.LoanContractStatus;
import com.loanapproval.dss.creditcheck.CustomerCreditCheckService;
import com.loanapproval.dss.loan.CollateralType;
import com.loanapproval.dss.loan.LoanApplicationVerificationService;
import com.loanapproval.dss.loan.LoanApprovalReassessmentService;
import com.loanapproval.dss.loan.LoanDocumentRepository;
import com.loanapproval.dss.loan.LoanDocumentStorageService;
import com.loanapproval.dss.loan.LoanPurpose;
import com.loanapproval.dss.loan.LoanRecord;
import com.loanapproval.dss.loan.LoanRepository;
import com.loanapproval.dss.loan.LoanSlaService;
import com.loanapproval.dss.loan.LoanStatus;
import com.loanapproval.dss.loan.LoanStatusHistoryService;
import com.loanapproval.dss.loan.LoanType;
import com.loanapproval.dss.notification.NotificationService;
import com.loanapproval.dss.profile.CustomerProfile;
import com.loanapproval.dss.profile.CustomerProfileRepository;
import com.loanapproval.dss.repayment.OverdueLoanResolutionService;
import com.loanapproval.dss.repayment.RepaymentScheduleService;
import com.loanapproval.dss.staff.dto.StaffDecisionRequest;
import com.loanapproval.dss.staff.dto.StaffDecisionResponse;
import com.loanapproval.dss.staff.dto.StaffRequestDetailResponse;
import com.loanapproval.dss.verification.CustomerVerification;
import com.loanapproval.dss.verification.VerificationStatus;
import com.loanapproval.dss.verification.dto.UpdateCustomerVerificationRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class StaffReviewServiceTest {

    @Mock
    private StaffReviewRepository staffReviewRepository;

    @Mock
    private LoanRepository loanRepository;

    @Mock
    private LoanDocumentRepository loanDocumentRepository;

    @Mock
    private LoanDocumentStorageService loanDocumentStorageService;

    @Mock
    private LoanContractService loanContractService;

    @Mock
    private LoanApplicationVerificationService loanApplicationVerificationService;

    @Mock
    private CustomerCreditCheckService customerCreditCheckService;

    @Mock
    private CustomerProfileRepository customerProfileRepository;

    @Mock
    private ComplianceAuditService complianceAuditService;

    @Mock
    private LoanApprovalReassessmentService loanApprovalReassessmentService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private LoanStatusHistoryService loanStatusHistoryService;

    @Mock
    private RepaymentScheduleService repaymentScheduleService;

    @Mock
    private OverdueLoanResolutionService overdueLoanResolutionService;

    @Mock
    private LoanSlaService loanSlaService;

    @InjectMocks
    private StaffReviewService staffReviewService;

    @Test
    void shouldKeepReviewQueueVisibleForPendingAndNeedsMoreInfo() {
        when(staffReviewRepository.findReviewQueue(LoanStatus.PENDING)).thenReturn(List.of());
        when(staffReviewRepository.findReviewQueue(LoanStatus.NEEDS_MORE_INFO)).thenReturn(List.of());

        assertThat(staffReviewService.listReviewQueue(LoanStatus.PENDING)).isEmpty();
        assertThat(staffReviewService.listReviewQueue(LoanStatus.NEEDS_MORE_INFO)).isEmpty();
        assertThatThrownBy(() -> staffReviewService.listReviewQueue(LoanStatus.APPROVED))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> {
                    ResponseStatusException exception = (ResponseStatusException) error;
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                });

        verify(staffReviewRepository).findReviewQueue(LoanStatus.PENDING);
        verify(staffReviewRepository).findReviewQueue(LoanStatus.NEEDS_MORE_INFO);
    }

    @Test
    void shouldExposePostDecisionLoansThroughOperationQueue() {
        when(staffReviewRepository.findOperationQueue(LoanStatus.CONTRACTED)).thenReturn(List.of());

        assertThat(staffReviewService.listOperationQueue(LoanStatus.CONTRACTED)).isEmpty();
        assertThatThrownBy(() -> staffReviewService.listOperationQueue(LoanStatus.APPOINTMENT_SCHEDULED))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> {
                    ResponseStatusException exception = (ResponseStatusException) error;
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                });

        verify(staffReviewRepository).findOperationQueue(LoanStatus.CONTRACTED);
    }

    @Test
    void shouldBlockAssigningOwnLoan() {
        Long staffUserId = 44L;
        LoanRecord ownLoan = loanRecord(98L, staffUserId, LoanType.UNSECURED, LoanStatus.PENDING);
        when(loanRepository.findById(98L)).thenReturn(Optional.of(ownLoan));

        assertThatThrownBy(() -> staffReviewService.assignCase(staffUserId, 98L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> {
                    ResponseStatusException exception = (ResponseStatusException) error;
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getReason()).contains("chính tài khoản nhân viên");
                });

        verify(staffReviewRepository, never()).assignCase(anyLong(), anyLong());
    }

    @Test
    void shouldBlockSubmittingDecisionForOwnLoan() {
        Long staffUserId = 45L;
        LoanRecord ownLoan = loanRecord(99L, staffUserId, LoanType.UNSECURED, LoanStatus.PENDING);
        when(loanRepository.findById(99L)).thenReturn(Optional.of(ownLoan));

        assertThatThrownBy(() -> staffReviewService.submitDecision(
                        staffUserId,
                        99L,
                        new StaffDecisionRequest(
                                StaffDecisionAction.REJECT,
                                null,
                                null,
                                "Xung đột lợi ích",
                                null,
                                null,
                                null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> {
                    ResponseStatusException exception = (ResponseStatusException) error;
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getReason()).contains("chính tài khoản nhân viên");
                });

        verify(staffReviewRepository, never()).findAssignedStaffUserId(anyLong());
        verify(staffReviewRepository, never()).insertDecisionAudit(anyLong(), anyLong(), any(), anyString());
    }

    @Test
    void shouldBlockDisbursingOwnLoan() {
        Long staffUserId = 66L;
        LoanRecord ownLoan = loanRecord(120L, staffUserId, LoanType.UNSECURED, LoanStatus.CONTRACTED);
        when(loanRepository.findById(120L)).thenReturn(Optional.of(ownLoan));

        assertThatThrownBy(() -> staffReviewService.disburseLoan(staffUserId, 120L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> {
                    ResponseStatusException exception = (ResponseStatusException) error;
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getReason()).contains("chính tài khoản nhân viên");
                });

        verify(loanRepository, never()).updateStatus(anyLong(), any());
        verify(notificationService, never()).notifyCustomerLoanDisbursed(anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    void shouldMoveSecuredApprovalToAppointmentScheduled() {
        Long loanRequestId = 99L;
        Long customerId = 44L;
        Long staffUserId = 8L;
        Instant scheduledAt = Instant.parse("2026-06-10T09:00:00Z");
        LoanRecord pendingLoan = loanRecord(loanRequestId, customerId, LoanType.SECURED, LoanStatus.PENDING);
        LoanRecord updatedLoan = loanRecord(loanRequestId, customerId, LoanType.SECURED, LoanStatus.APPOINTMENT_SCHEDULED);
        CustomerVerification verification = fullyVerified(customerId);
        StaffDecisionRequest request = new StaffDecisionRequest(
                StaffDecisionAction.APPROVE,
                scheduledAt,
                "Head Office",
                "Bring original registration",
                BigDecimal.valueOf(250_000_000),
                24,
                BigDecimal.valueOf(0.115000).setScale(6));
        LoanApprovalReassessmentService.ReassessmentResult reassessment =
                new LoanApprovalReassessmentService.ReassessmentResult(
                        BigDecimal.valueOf(280_000_000),
                        BigDecimal.valueOf(250_000_000),
                        24,
                        BigDecimal.valueOf(0.115000).setScale(6),
                        BigDecimal.valueOf(11_500_000),
                        "policy-v2",
                        "ok",
                        BigDecimal.valueOf(42),
                        false);

        when(loanRepository.findById(loanRequestId)).thenReturn(Optional.of(pendingLoan), Optional.of(updatedLoan));
        when(staffReviewRepository.findAssignedStaffUserId(loanRequestId)).thenReturn(Optional.of(staffUserId));
        when(loanApplicationVerificationService.getOrDefault(loanRequestId, customerId)).thenReturn(verification);
        when(loanApplicationVerificationService.isFullyVerified(LoanType.SECURED, verification)).thenReturn(true);
        when(loanApprovalReassessmentService.reassessAndPersist(
                        pendingLoan,
                        verification,
                        request.approvedAmount(),
                        request.approvedTermMonths(),
                        request.approvedAnnualRate(),
                        null,
                        false))
                .thenReturn(reassessment);
        when(loanRepository.updateDecision(
                        eq(loanRequestId),
                        eq(LoanStatus.APPOINTMENT_SCHEDULED),
                        anyString(),
                        eq(reassessment.eligibleLimit()),
                        eq(reassessment.approvedAmount()),
                        eq(reassessment.approvedTermMonths()),
                        eq(reassessment.approvedAnnualRate()),
                        eq(reassessment.approvedMonthlyPayment()),
                        eq(reassessment.decisionPolicyVersion())))
                .thenReturn(1);
        when(staffReviewRepository.findRequestDetailById(loanRequestId))
                .thenReturn(Optional.of(detail(updatedLoan, scheduledAt)));
        when(staffReviewRepository.findDecisionAuditsByLoanRequestId(loanRequestId)).thenReturn(List.of());
        when(loanDocumentRepository.findByLoanRequestId(loanRequestId)).thenReturn(List.of());
        when(customerCreditCheckService.findLatestByCustomerId(customerId)).thenReturn(Optional.empty());

        StaffDecisionResponse response = staffReviewService.submitDecision(staffUserId, loanRequestId, request);

        assertThat(response.status()).isEqualTo(LoanStatus.APPOINTMENT_SCHEDULED);
        verify(staffReviewRepository)
                .insertAppointment(
                        loanRequestId,
                        customerId,
                        staffUserId,
                        scheduledAt,
                        "Head Office",
                        "Bring original registration");
        verify(loanRepository)
                .updateDecision(
                        eq(loanRequestId),
                        eq(LoanStatus.APPOINTMENT_SCHEDULED),
                        anyString(),
                        eq(reassessment.eligibleLimit()),
                        eq(reassessment.approvedAmount()),
                        eq(reassessment.approvedTermMonths()),
                        eq(reassessment.approvedAnnualRate()),
                        eq(reassessment.approvedMonthlyPayment()),
                        eq(reassessment.decisionPolicyVersion()));
        verify(notificationService)
                .notifyCustomerLoanDecisionUpdated(
                        eq(loanRequestId),
                        eq(customerId),
                        eq(staffUserId),
                        eq(LoanType.SECURED),
                        eq(LoanStatus.APPOINTMENT_SCHEDULED),
                        contains("Head Office"),
                        eq(false),
                        eq(null));
        verify(notificationService)
                .notifyCustomerAppointmentScheduled(
                        loanRequestId,
                        customerId,
                        staffUserId,
                        scheduledAt,
                        "Head Office");
    }

    @Test
    void shouldCreatePendingContractPreviewWhenApprovingUnsecuredLoan() {
        Long loanRequestId = 100L;
        Long customerId = 45L;
        Long staffUserId = 8L;
        LoanRecord pendingLoan = loanRecord(loanRequestId, customerId, LoanType.UNSECURED, LoanStatus.PENDING);
        LoanRecord updatedLoan = loanRecord(loanRequestId, customerId, LoanType.UNSECURED, LoanStatus.APPROVED);
        CustomerVerification verification = fullyVerified(customerId);
        StaffDecisionRequest request = new StaffDecisionRequest(
                StaffDecisionAction.APPROVE,
                null,
                null,
                null,
                BigDecimal.valueOf(220_000_000),
                24,
                BigDecimal.valueOf(0.115000).setScale(6));
        LoanApprovalReassessmentService.ReassessmentResult reassessment =
                new LoanApprovalReassessmentService.ReassessmentResult(
                        BigDecimal.valueOf(240_000_000),
                        BigDecimal.valueOf(220_000_000),
                        24,
                        BigDecimal.valueOf(0.115000).setScale(6),
                        BigDecimal.valueOf(10_800_000),
                        "policy-v2",
                        "ok",
                        BigDecimal.valueOf(42),
                        false);

        when(loanRepository.findById(loanRequestId)).thenReturn(Optional.of(pendingLoan), Optional.of(updatedLoan));
        when(staffReviewRepository.findAssignedStaffUserId(loanRequestId)).thenReturn(Optional.of(staffUserId));
        when(loanApplicationVerificationService.getOrDefault(loanRequestId, customerId)).thenReturn(verification);
        when(loanApplicationVerificationService.isFullyVerified(LoanType.UNSECURED, verification)).thenReturn(true);
        when(loanApprovalReassessmentService.reassessAndPersist(
                        pendingLoan,
                        verification,
                        request.approvedAmount(),
                        request.approvedTermMonths(),
                        request.approvedAnnualRate(),
                        null,
                        false))
                .thenReturn(reassessment);
        when(loanRepository.updateDecision(
                        eq(loanRequestId),
                        eq(LoanStatus.APPROVED),
                        anyString(),
                        eq(reassessment.eligibleLimit()),
                        eq(reassessment.approvedAmount()),
                        eq(reassessment.approvedTermMonths()),
                        eq(reassessment.approvedAnnualRate()),
                        eq(reassessment.approvedMonthlyPayment()),
                        eq(reassessment.decisionPolicyVersion())))
                .thenReturn(1);
        when(staffReviewRepository.findRequestDetailById(loanRequestId))
                .thenReturn(Optional.of(detail(updatedLoan, null)));
        when(staffReviewRepository.findDecisionAuditsByLoanRequestId(loanRequestId)).thenReturn(List.of());
        when(loanDocumentRepository.findByLoanRequestId(loanRequestId)).thenReturn(List.of());
        when(customerCreditCheckService.findLatestByCustomerId(customerId)).thenReturn(Optional.empty());

        StaffDecisionResponse response = staffReviewService.submitDecision(staffUserId, loanRequestId, request);

        assertThat(response.status()).isEqualTo(LoanStatus.APPROVED);
        verify(loanContractService).createIfMissingFromApprovedLoan(updatedLoan, staffUserId);
    }

    @Test
    void shouldBlockDirectContractCompletionForSecuredLoan() {
        Long loanRequestId = 109L;
        LoanRecord approvedSecuredLoan = loanRecord(loanRequestId, 55L, LoanType.SECURED, LoanStatus.APPROVED);

        when(loanRepository.findById(loanRequestId)).thenReturn(Optional.of(approvedSecuredLoan));

        assertThatThrownBy(() -> staffReviewService.completeContract(9L, loanRequestId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> {
                    ResponseStatusException exception = (ResponseStatusException) error;
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getReason()).contains("Khoản vay thế chấp phải hoàn tất hợp đồng");
                });

        verify(loanContractService, never()).createIfMissingFromApprovedLoan(eq(approvedSecuredLoan), eq(9L));
        verify(loanRepository, never()).updateStatus(anyLong(), eq(LoanStatus.CONTRACTED));
    }

    @Test
    void shouldMoveLoanToActiveAfterDisbursement() {
        Long loanRequestId = 120L;
        Long customerId = 66L;
        Long staffUserId = 9L;
        LoanRecord contractedLoan = loanRecord(loanRequestId, customerId, LoanType.UNSECURED, LoanStatus.CONTRACTED);
        LoanRecord activeLoan = loanRecord(loanRequestId, customerId, LoanType.UNSECURED, LoanStatus.ACTIVE);

        when(loanRepository.findById(loanRequestId)).thenReturn(Optional.of(contractedLoan), Optional.of(activeLoan));
        when(staffReviewRepository.assignCase(loanRequestId, staffUserId)).thenReturn(1);
        when(loanContractService.findByLoanRequestId(loanRequestId)).thenReturn(activeContract());
        when(customerProfileRepository.findByUserId(customerId)).thenReturn(Optional.of(disbursementProfile(customerId)));
        when(staffReviewRepository.findRequestDetailById(loanRequestId)).thenReturn(Optional.of(detail(activeLoan, null)));
        when(staffReviewRepository.findDecisionAuditsByLoanRequestId(loanRequestId)).thenReturn(List.of());
        when(loanDocumentRepository.findByLoanRequestId(loanRequestId)).thenReturn(List.of());
        when(customerCreditCheckService.findLatestByCustomerId(customerId)).thenReturn(Optional.empty());

        StaffRequestDetailResponse response = staffReviewService.disburseLoan(staffUserId, loanRequestId);

        assertThat(response.status()).isEqualTo(LoanStatus.ACTIVE);
        verify(loanRepository).updateStatus(loanRequestId, LoanStatus.ACTIVE);
        verify(notificationService).notifyCustomerLoanDisbursed(
                loanRequestId,
                customerId,
                staffUserId,
                BigDecimal.valueOf(250_000_000));
    }

    @Test
    void shouldBlockDisbursementWhenCustomerHasNoBankAccount() {
        Long loanRequestId = 121L;
        Long customerId = 67L;
        Long staffUserId = 9L;
        LoanRecord contractedLoan = loanRecord(loanRequestId, customerId, LoanType.UNSECURED, LoanStatus.CONTRACTED);

        when(loanRepository.findById(loanRequestId)).thenReturn(Optional.of(contractedLoan));
        when(staffReviewRepository.assignCase(loanRequestId, staffUserId)).thenReturn(1);
        when(loanContractService.findByLoanRequestId(loanRequestId)).thenReturn(activeContract());
        when(customerProfileRepository.findByUserId(customerId)).thenReturn(Optional.of(profileWithoutDisbursementAccount(customerId)));

        assertThatThrownBy(() -> staffReviewService.disburseLoan(staffUserId, loanRequestId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> {
                    ResponseStatusException exception = (ResponseStatusException) error;
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getReason()).contains("tài khoản");
                });

        verify(loanRepository, never()).updateStatus(loanRequestId, LoanStatus.ACTIVE);
        verify(notificationService, never()).notifyCustomerLoanDisbursed(anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    void shouldBlockVerificationUpdateAfterLoanHasBeenApproved() {
        Long loanRequestId = 130L;
        Long customerId = 70L;
        Long staffUserId = 10L;
        LoanRecord approvedLoan = loanRecord(loanRequestId, customerId, LoanType.UNSECURED, LoanStatus.APPROVED);
        UpdateCustomerVerificationRequest request = new UpdateCustomerVerificationRequest(
                VerificationStatus.PASSED,
                VerificationStatus.PASSED,
                VerificationStatus.PASSED,
                VerificationStatus.PASSED,
                BigDecimal.valueOf(45_000_000),
                VerificationStatus.PASSED,
                VerificationStatus.PASSED,
                false,
                "Đã đối chiếu");

        when(loanRepository.findById(loanRequestId)).thenReturn(Optional.of(approvedLoan));
        when(staffReviewRepository.assignCase(loanRequestId, staffUserId)).thenReturn(1);

        assertThatThrownBy(() -> staffReviewService.updateVerification(staffUserId, loanRequestId, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> {
                    ResponseStatusException exception = (ResponseStatusException) error;
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getReason()).contains("Chỉ được cập nhật xác minh");
                });

        verify(loanApplicationVerificationService, never()).update(anyLong(), anyLong(), anyLong(), any());
    }

    private LoanRecord loanRecord(Long id, Long customerId, LoanType loanType, LoanStatus status) {
        Instant now = Instant.now();
        return new LoanRecord(
                id,
                customerId,
                loanType,
                BigDecimal.valueOf(300_000_000),
                36,
                LoanPurpose.BUSINESS,
                loanType == LoanType.SECURED ? CollateralType.VEHICLE_REGISTRATION : null,
                loanType == LoanType.SECURED ? BigDecimal.valueOf(450_000_000) : null,
                status,
                null,
                BigDecimal.valueOf(320_000_000),
                BigDecimal.valueOf(250_000_000),
                24,
                BigDecimal.valueOf(0.115000).setScale(6),
                BigDecimal.valueOf(11_500_000),
                "policy-v2",
                "intake",
                now,
                now);
    }

    private StaffRequestDetailResponse detail(LoanRecord loan, Instant scheduledAt) {
        Instant now = Instant.now();
        return new StaffRequestDetailResponse(
                loan.id(),
                loan.loanType(),
                loan.status(),
                loan.amount(),
                loan.termMonths(),
                loan.purpose(),
                loan.collateralType(),
                "Lịch hẹn gặp mặt",
                loan.eligibleLimit(),
                loan.approvedAmount(),
                loan.approvedTermMonths(),
                loan.approvedAnnualRate(),
                loan.approvedMonthlyPayment(),
                loan.decisionPolicyVersion(),
                loan.intakeNote(),
                now,
                now,
                new StaffRequestDetailResponse.CustomerSummary(loan.customerId(), "customer@example.com"),
                null,
                null,
                null,
                null,
                null,
                null,
                new StaffRequestDetailResponse.AppointmentSummary(
                        1L,
                        8L,
                        "staff@example.com",
                        scheduledAt,
                        "Head Office",
                        "Bring original registration",
                        "SCHEDULED",
                        now),
                List.of(),
                List.of());
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
                1L,
                now,
                now,
                now);
    }

    private LoanContract activeContract() {
        Instant now = Instant.now();
        return new LoanContract(
                77L,
                120L,
                66L,
                BigDecimal.valueOf(250_000_000),
                BigDecimal.valueOf(0.115000).setScale(6),
                24,
                java.time.LocalDate.now(),
                java.time.LocalDate.now().plusMonths(24),
                java.time.LocalDate.now().plusMonths(1),
                "15",
                java.time.LocalDate.now().plusMonths(24),
                BigDecimal.valueOf(11_500_000),
                BigDecimal.valueOf(26_000_000),
                LoanContractStatus.ACTIVE,
                now,
                now);
    }

    private CustomerProfile disbursementProfile(Long customerId) {
        return new CustomerProfile(
                customerId,
                "Nguyễn Minh An",
                "0901234567",
                "012345678901",
                java.time.LocalDate.of(1994, 5, 12),
                BigDecimal.valueOf(30_000_000),
                BigDecimal.valueOf(28_000_000),
                BigDecimal.valueOf(15),
                "EMPLOYED",
                java.time.LocalDate.of(2020, 1, 1),
                "19036866889922",
                "Vietcombank",
                740,
                25,
                "payslip.pdf",
                "stored-payslip.pdf",
                "application/pdf",
                1024L,
                Instant.now(),
                "id-front.jpg",
                "stored-front.jpg",
                "image/jpeg",
                2048L,
                Instant.now(),
                "id-back.jpg",
                "stored-back.jpg",
                "image/jpeg",
                2048L,
                Instant.now());
    }

    private CustomerProfile profileWithoutDisbursementAccount(Long customerId) {
        return new CustomerProfile(
                customerId,
                "Nguyễn Minh An",
                "0901234567",
                "012345678901",
                java.time.LocalDate.of(1994, 5, 12),
                BigDecimal.valueOf(30_000_000),
                BigDecimal.valueOf(28_000_000),
                BigDecimal.valueOf(15),
                "EMPLOYED",
                java.time.LocalDate.of(2020, 1, 1),
                null,
                null,
                740,
                25,
                "payslip.pdf",
                "stored-payslip.pdf",
                "application/pdf",
                1024L,
                Instant.now(),
                "id-front.jpg",
                "stored-front.jpg",
                "image/jpeg",
                2048L,
                Instant.now(),
                "id-back.jpg",
                "stored-back.jpg",
                "image/jpeg",
                2048L,
                Instant.now());
    }
}
