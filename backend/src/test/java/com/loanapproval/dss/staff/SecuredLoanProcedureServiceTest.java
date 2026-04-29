package com.loanapproval.dss.staff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loanapproval.dss.compliance.ComplianceAuditService;
import com.loanapproval.dss.contract.LoanContractScheduleTerms;
import com.loanapproval.dss.contract.LoanContractService;
import com.loanapproval.dss.customerinfo.CustomerInformationVerificationService;
import com.loanapproval.dss.loan.CollateralType;
import com.loanapproval.dss.loan.LoanApprovalReassessmentService;
import com.loanapproval.dss.loan.LoanPurpose;
import com.loanapproval.dss.loan.LoanRecord;
import com.loanapproval.dss.loan.LoanRepository;
import com.loanapproval.dss.loan.LoanStatus;
import com.loanapproval.dss.loan.LoanType;
import com.loanapproval.dss.staff.dto.StaffRequestDetailResponse;
import com.loanapproval.dss.staff.dto.StaffSecuredProcedureRequest;
import com.loanapproval.dss.staff.dto.StaffSecuredProcedureResponse;
import com.loanapproval.dss.verification.CustomerVerification;
import com.loanapproval.dss.verification.CustomerVerificationService;
import com.loanapproval.dss.verification.VerificationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class SecuredLoanProcedureServiceTest {

    @Mock
    private SecuredLoanProcedureRepository securedLoanProcedureRepository;

    @Mock
    private LoanRepository loanRepository;

    @Mock
    private LoanContractService loanContractService;

    @Mock
    private ComplianceAuditService complianceAuditService;

    @Mock
    private CustomerInformationVerificationService customerInformationVerificationService;

    @Mock
    private CustomerVerificationService customerVerificationService;

    @Mock
    private LoanApprovalReassessmentService loanApprovalReassessmentService;

    @InjectMocks
    private SecuredLoanProcedureService securedLoanProcedureService;

    @Test
    void shouldAllowCompletionWhenSimulatedTimeIsAfterAppointment() {
        Long loanRequestId = 14L;
        Long customerId = 21L;
        Long staffUserId = 8L;
        Instant scheduledAt = Instant.parse("2026-04-29T02:00:00Z");
        Instant effectiveNow = scheduledAt.plusSeconds(2 * 60 * 60);

        LoanRecord appointmentScheduledLoan =
                loanRecord(loanRequestId, customerId, LoanStatus.APPOINTMENT_SCHEDULED);
        LoanRecord approvedLoan = loanRecord(loanRequestId, customerId, LoanStatus.APPROVED);
        LoanRecord contractedLoan = loanRecord(loanRequestId, customerId, LoanStatus.CONTRACTED);
        StaffSecuredProcedureRequest request = completedRequest();
        StaffSecuredProcedureResponse currentDetail =
                detailResponse(loanRequestId, customerId, LoanStatus.APPOINTMENT_SCHEDULED, SecuredProcedureStatus.IN_PROGRESS, scheduledAt);
        StaffSecuredProcedureResponse completedDetail =
                detailResponse(loanRequestId, customerId, LoanStatus.CONTRACTED, SecuredProcedureStatus.COMPLETED, scheduledAt);
        CustomerVerification verification = fullyVerified(customerId);
        BigDecimal requestedAnnualRate = request.monthlyInterestRate()
                .multiply(BigDecimal.valueOf(12))
                .setScale(6);
        LoanApprovalReassessmentService.ReassessmentResult reassessment =
                new LoanApprovalReassessmentService.ReassessmentResult(
                        BigDecimal.valueOf(280_000_000),
                        BigDecimal.valueOf(250_000_000),
                        24,
                        BigDecimal.valueOf(0.115000).setScale(6),
                        BigDecimal.valueOf(11_500_000),
                        "policy-v2",
                        "reassessed",
                        BigDecimal.valueOf(42),
                        false);

        when(loanRepository.findById(loanRequestId))
                .thenReturn(Optional.of(appointmentScheduledLoan), Optional.of(approvedLoan), Optional.of(contractedLoan));
        when(securedLoanProcedureRepository.findByLoanRequestId(loanRequestId))
                .thenReturn(Optional.of(currentDetail), Optional.of(completedDetail));
        when(customerVerificationService.getOrDefault(customerId)).thenReturn(verification);
        when(loanApprovalReassessmentService.reassessAndPersist(
                        appointmentScheduledLoan,
                        verification,
                        appointmentScheduledLoan.approvedAmount(),
                        appointmentScheduledLoan.approvedTermMonths(),
                        requestedAnnualRate,
                        request.appraisalValue(),
                        true))
                .thenReturn(reassessment);

        StaffSecuredProcedureResponse response =
                securedLoanProcedureService.saveSecuredProcedure(staffUserId, loanRequestId, request, effectiveNow);

        assertThat(response.status()).isEqualTo(SecuredProcedureStatus.COMPLETED);
        assertThat(response.loanStatus()).isEqualTo(LoanStatus.CONTRACTED);
        verify(securedLoanProcedureRepository).upsert(loanRequestId, staffUserId, request);
        verify(loanApprovalReassessmentService)
                .reassessAndPersist(
                        appointmentScheduledLoan,
                        verification,
                        appointmentScheduledLoan.approvedAmount(),
                        appointmentScheduledLoan.approvedTermMonths(),
                        requestedAnnualRate,
                        request.appraisalValue(),
                        true);
        verify(loanRepository)
                .updateDecision(
                        eq(loanRequestId),
                        eq(LoanStatus.APPROVED),
                        anyString(),
                        eq(reassessment.eligibleLimit()),
                        eq(reassessment.approvedAmount()),
                        eq(reassessment.approvedTermMonths()),
                        eq(reassessment.approvedAnnualRate()),
                        eq(reassessment.approvedMonthlyPayment()),
                        eq(reassessment.decisionPolicyVersion()));
        verify(securedLoanProcedureRepository).markLatestAppointmentCompleted(loanRequestId);
        ArgumentCaptor<LoanContractScheduleTerms> scheduleTermsCaptor =
                ArgumentCaptor.forClass(LoanContractScheduleTerms.class);
        verify(loanContractService).createIfMissingFromApprovedLoan(
                eq(approvedLoan),
                eq(staffUserId),
                scheduleTermsCaptor.capture());
        LoanContractScheduleTerms scheduleTerms = scheduleTermsCaptor.getValue();
        assertThat(scheduleTerms.annualInterestRate()).isEqualByComparingTo(reassessment.approvedAnnualRate());
        assertThat(scheduleTerms.monthlyPayment()).isEqualByComparingTo(reassessment.approvedMonthlyPayment());
        verify(loanRepository).updateStatus(loanRequestId, LoanStatus.CONTRACTED);
    }

    @Test
    void shouldRejectCompletionWhenSimulatedTimeIsStillBeforeAppointment() {
        Long loanRequestId = 14L;
        Long customerId = 21L;
        Long staffUserId = 8L;
        Instant scheduledAt = Instant.parse("2026-04-29T02:00:00Z");
        Instant effectiveNow = scheduledAt.minusSeconds(120);
        LoanRecord appointmentScheduledLoan =
                loanRecord(loanRequestId, customerId, LoanStatus.APPOINTMENT_SCHEDULED);
        StaffSecuredProcedureRequest request = completedRequest();
        StaffSecuredProcedureResponse currentDetail =
                detailResponse(loanRequestId, customerId, LoanStatus.APPOINTMENT_SCHEDULED, SecuredProcedureStatus.IN_PROGRESS, scheduledAt);

        when(loanRepository.findById(loanRequestId)).thenReturn(Optional.of(appointmentScheduledLoan));
        when(securedLoanProcedureRepository.findByLoanRequestId(loanRequestId)).thenReturn(Optional.of(currentDetail));

        assertThatThrownBy(
                        () -> securedLoanProcedureService.saveSecuredProcedure(
                                staffUserId,
                                loanRequestId,
                                request,
                                effectiveNow))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> {
                    ResponseStatusException exception = (ResponseStatusException) error;
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getReason()).contains("buổi gặp mặt");
                });

        verify(securedLoanProcedureRepository, never()).upsert(loanRequestId, staffUserId, request);
        verify(loanApprovalReassessmentService, never())
                .reassessAndPersist(
                        eq(appointmentScheduledLoan),
                        eq(fullyVerified(customerId)),
                        eq(appointmentScheduledLoan.approvedAmount()),
                        eq(appointmentScheduledLoan.approvedTermMonths()),
                        eq(appointmentScheduledLoan.approvedAnnualRate()),
                        eq(request.appraisalValue()),
                        eq(true));
    }

    @Test
    void shouldReportSpecificMissingFieldsForCompletion() {
        Long loanRequestId = 17L;
        Long customerId = 21L;
        Long staffUserId = 8L;
        Instant scheduledAt = Instant.parse("2026-05-01T16:21:59Z");
        Instant effectiveNow = scheduledAt.plusSeconds(2 * 60 * 60);
        LoanRecord appointmentScheduledLoan =
                loanRecord(loanRequestId, customerId, LoanStatus.APPOINTMENT_SCHEDULED);
        StaffSecuredProcedureRequest request = new StaffSecuredProcedureRequest(
                "Ngân hàng Demo",
                "1 Demo Street",
                "0102030405",
                "0909000000",
                "HDTC-2026-017",
                LocalDate.of(2026, 5, 1),
                "Việt Nam",
                "079123456789",
                "12 Nguyễn Huệ, Quận 1",
                "12 Nguyễn Huệ, Quận 1",
                "Nhân viên kỹ thuật",
                "Kỹ sư",
                "Ô tô",
                "Toyota",
                "ENG-017",
                "FRAME-017",
                "Nguyễn Minh An",
                "VIN-017",
                "51A-12345",
                BigDecimal.valueOf(320_000_000),
                BigDecimal.valueOf(40_000_000),
                BigDecimal.valueOf(280_000_000),
                BigDecimal.valueOf(0.120000).setScale(6),
                BigDecimal.valueOf(3_400_000),
                null,
                "123",
                null,
                "APR-2026-017",
                "BH-017",
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                SecuredProcedureStatus.COMPLETED,
                "Thiếu lịch trả nợ");
        StaffSecuredProcedureResponse currentDetail =
                detailResponse(loanRequestId, customerId, LoanStatus.APPOINTMENT_SCHEDULED, SecuredProcedureStatus.IN_PROGRESS, scheduledAt);

        when(loanRepository.findById(loanRequestId)).thenReturn(Optional.of(appointmentScheduledLoan));
        when(securedLoanProcedureRepository.findByLoanRequestId(loanRequestId)).thenReturn(Optional.of(currentDetail));

        assertThatThrownBy(
                        () -> securedLoanProcedureService.saveSecuredProcedure(
                                staffUserId,
                                loanRequestId,
                                request,
                                effectiveNow))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> {
                    ResponseStatusException exception = (ResponseStatusException) error;
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getReason()).contains("4.5 Ngày thanh toán đầu tiên");
                    assertThat(exception.getReason()).contains("4.7 Ngày thanh toán cuối cùng");
                });
    }

    private LoanRecord loanRecord(Long loanRequestId, Long customerId, LoanStatus status) {
        Instant now = Instant.now();
        return new LoanRecord(
                loanRequestId,
                customerId,
                LoanType.SECURED,
                BigDecimal.valueOf(300_000_000),
                24,
                LoanPurpose.BUSINESS,
                CollateralType.VEHICLE_REGISTRATION,
                status,
                null,
                BigDecimal.valueOf(280_000_000),
                BigDecimal.valueOf(250_000_000),
                24,
                BigDecimal.valueOf(0.115000).setScale(6),
                BigDecimal.valueOf(11_500_000),
                "policy-v2",
                "intake",
                now,
                now);
    }

    private StaffSecuredProcedureRequest completedRequest() {
        return new StaffSecuredProcedureRequest(
                "Ngân hàng Demo",
                "1 Demo Street",
                "0102030405",
                "0909000000",
                "HDTC-2026-001",
                LocalDate.of(2026, 4, 29),
                "Việt Nam",
                "079123456789",
                "12 Nguyễn Huệ, Quận 1",
                "12 Nguyễn Huệ, Quận 1",
                "Nhân viên kỹ thuật",
                "Kỹ sư",
                "Ô tô",
                "Toyota",
                "ENG-001",
                "FRAME-001",
                "Nguyễn Minh An",
                "VIN-001",
                "51A-12345",
                BigDecimal.valueOf(320_000_000),
                BigDecimal.valueOf(70_000_000),
                BigDecimal.valueOf(300_000_000),
                BigDecimal.valueOf(0.009583).setScale(6),
                BigDecimal.valueOf(11_500_000),
                LocalDate.of(2026, 5, 10),
                "10",
                LocalDate.of(2028, 4, 10),
                "APR-2026-001",
                "BH-001",
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                SecuredProcedureStatus.COMPLETED,
                "Hoàn tất cho buổi demo");
    }

    private StaffSecuredProcedureResponse detailResponse(
            Long loanRequestId,
            Long customerId,
            LoanStatus loanStatus,
            SecuredProcedureStatus procedureStatus,
            Instant scheduledAt) {
        Instant now = Instant.now();
        return new StaffSecuredProcedureResponse(
                loanRequestId,
                customerId,
                "customer.demo@loan.local",
                "Nguyễn Minh An",
                "0901234567",
                LocalDate.of(1994, 5, 12),
                "Nhân viên kỹ thuật",
                BigDecimal.valueOf(300_000_000),
                24,
                loanStatus,
                new StaffRequestDetailResponse.AppointmentSummary(
                        88L,
                        8L,
                        "staff.demo@loan.local",
                        scheduledAt,
                        "Hội sở",
                        "Mang bản gốc giấy tờ xe",
                        "SCHEDULED",
                        now),
                99L,
                8L,
                "staff.demo@loan.local",
                "Ngân hàng Demo",
                "1 Demo Street",
                "0102030405",
                "0909000000",
                "HDTC-2026-001",
                LocalDate.of(2026, 4, 29),
                "Việt Nam",
                "079123456789",
                "12 Nguyễn Huệ, Quận 1",
                "12 Nguyễn Huệ, Quận 1",
                "Nhân viên kỹ thuật",
                "Kỹ sư",
                "Ô tô",
                "Toyota",
                "ENG-001",
                "FRAME-001",
                "Nguyễn Minh An",
                "VIN-001",
                "51A-12345",
                BigDecimal.valueOf(320_000_000),
                BigDecimal.valueOf(70_000_000),
                BigDecimal.valueOf(300_000_000),
                BigDecimal.valueOf(0.009583).setScale(6),
                BigDecimal.valueOf(11_500_000),
                LocalDate.of(2026, 5, 10),
                "10",
                LocalDate.of(2028, 4, 10),
                "APR-2026-001",
                "BH-001",
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                procedureStatus,
                "Đã lưu mẫu",
                procedureStatus == SecuredProcedureStatus.COMPLETED ? now : null,
                now);
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
                8L,
                now,
                now,
                now);
    }
}
