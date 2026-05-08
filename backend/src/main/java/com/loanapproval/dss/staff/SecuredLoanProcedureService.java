package com.loanapproval.dss.staff;

import com.loanapproval.dss.compliance.ComplianceAuditService;
import com.loanapproval.dss.compliance.ComplianceOutcome;
import com.loanapproval.dss.contract.LoanContractStatus;
import com.loanapproval.dss.contract.LoanContractScheduleTerms;
import com.loanapproval.dss.contract.LoanContractService;
import com.loanapproval.dss.customerinfo.CustomerInformationVerificationService;
import com.loanapproval.dss.loan.LoanApprovalReassessmentService;
import com.loanapproval.dss.loan.LoanRecord;
import com.loanapproval.dss.loan.LoanRepository;
import com.loanapproval.dss.loan.LoanStatus;
import com.loanapproval.dss.loan.LoanStatusHistoryService;
import com.loanapproval.dss.loan.LoanType;
import com.loanapproval.dss.notification.NotificationService;
import com.loanapproval.dss.staff.dto.StaffAppointmentRequest;
import com.loanapproval.dss.staff.dto.StaffSecuredProcedureRequest;
import com.loanapproval.dss.staff.dto.StaffSecuredProcedureResponse;
import com.loanapproval.dss.staff.dto.StaffSecuredProcedureSummaryResponse;
import com.loanapproval.dss.verification.CustomerVerification;
import com.loanapproval.dss.verification.CustomerVerificationService;
import com.loanapproval.dss.verification.VerificationStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SecuredLoanProcedureService {

    private static final BigDecimal MONTHLY_PAYMENT_TOLERANCE = BigDecimal.ONE;

    private final SecuredLoanProcedureRepository securedLoanProcedureRepository;
    private final LoanRepository loanRepository;
    private final LoanContractService loanContractService;
    private final ComplianceAuditService complianceAuditService;
    private final CustomerInformationVerificationService customerInformationVerificationService;
    private final CustomerVerificationService customerVerificationService;
    private final LoanApprovalReassessmentService loanApprovalReassessmentService;
    private final LoanStatusHistoryService loanStatusHistoryService;
    private final NotificationService notificationService;

    public SecuredLoanProcedureService(
            SecuredLoanProcedureRepository securedLoanProcedureRepository,
            LoanRepository loanRepository,
            LoanContractService loanContractService,
            ComplianceAuditService complianceAuditService,
            CustomerInformationVerificationService customerInformationVerificationService,
            CustomerVerificationService customerVerificationService,
            LoanApprovalReassessmentService loanApprovalReassessmentService,
            LoanStatusHistoryService loanStatusHistoryService,
            NotificationService notificationService) {
        this.securedLoanProcedureRepository = securedLoanProcedureRepository;
        this.loanRepository = loanRepository;
        this.loanContractService = loanContractService;
        this.complianceAuditService = complianceAuditService;
        this.customerInformationVerificationService = customerInformationVerificationService;
        this.customerVerificationService = customerVerificationService;
        this.loanApprovalReassessmentService = loanApprovalReassessmentService;
        this.loanStatusHistoryService = loanStatusHistoryService;
        this.notificationService = notificationService;
    }

    public List<StaffSecuredProcedureSummaryResponse> listSecuredProcedures() {
        return securedLoanProcedureRepository.findSecuredProcedureQueue();
    }

    public StaffSecuredProcedureResponse getSecuredProcedure(Long loanRequestId) {
        assertSecuredLoan(loanRequestId);
        return securedLoanProcedureRepository.findByLoanRequestId(loanRequestId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy hồ sơ vay thế chấp"));
    }

    @Transactional
    public StaffSecuredProcedureResponse rescheduleAppointment(
            Long staffUserId,
            Long loanRequestId,
            StaffAppointmentRequest request) {
        LoanRecord loan = assertSecuredLoan(loanRequestId);
        ensureCaseAssignedTo(staffUserId, loanRequestId);
        assertAppointmentManageable(loan);
        if (request.scheduledAt() == null || request.scheduledAt().isBefore(Instant.now().minusSeconds(60))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lịch hẹn mới phải nằm trong tương lai");
        }
        int updated = securedLoanProcedureRepository.rescheduleLatestAppointment(
                loanRequestId,
                staffUserId,
                request.scheduledAt(),
                request.location(),
                request.note());
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không tìm thấy lịch hẹn hợp lệ để đổi lịch");
        }
        loanRepository.updateFinalReason(
                loanRequestId,
                buildAppointmentReason(request.scheduledAt(), request.location(), request.note()));
        complianceAuditService.log(
                loan.customerId(),
                loan.id(),
                staffUserId,
                "SECURED_APPOINTMENT_RESCHEDULED",
                ComplianceOutcome.INFO,
                "appointment rescheduled to " + request.scheduledAt());
        notificationService.notifyCustomerAppointmentScheduled(
                loanRequestId,
                loan.customerId(),
                staffUserId,
                request.scheduledAt(),
                request.location());
        return getSecuredProcedure(loanRequestId);
    }

    @Transactional
    public StaffSecuredProcedureResponse cancelAppointment(Long staffUserId, Long loanRequestId) {
        LoanRecord loan = assertSecuredLoan(loanRequestId);
        ensureCaseAssignedTo(staffUserId, loanRequestId);
        assertAppointmentManageable(loan);
        int updated = securedLoanProcedureRepository.cancelLatestAppointment(loanRequestId);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không tìm thấy lịch hẹn đang chờ để hủy");
        }
        complianceAuditService.log(
                loan.customerId(),
                loan.id(),
                staffUserId,
                "SECURED_APPOINTMENT_CANCELLED",
                ComplianceOutcome.INFO,
                "appointment cancelled by staff");
        return getSecuredProcedure(loanRequestId);
    }

    @Transactional
    public StaffSecuredProcedureResponse markAppointmentNoShow(Long staffUserId, Long loanRequestId) {
        LoanRecord loan = assertSecuredLoan(loanRequestId);
        ensureCaseAssignedTo(staffUserId, loanRequestId);
        assertAppointmentManageable(loan);
        int updated = securedLoanProcedureRepository.markLatestAppointmentNoShow(loanRequestId);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không tìm thấy lịch hẹn đang chờ để đánh dấu khách vắng mặt");
        }
        complianceAuditService.log(
                loan.customerId(),
                loan.id(),
                staffUserId,
                "SECURED_APPOINTMENT_NO_SHOW",
                ComplianceOutcome.INFO,
                "appointment marked as no-show by staff");
        notificationService.notifyCustomerAppointmentNoShow(loanRequestId, loan.customerId(), staffUserId);
        return getSecuredProcedure(loanRequestId);
    }

    @Transactional
    public StaffSecuredProcedureResponse saveSecuredProcedure(
            Long staffUserId,
            Long loanRequestId,
            StaffSecuredProcedureRequest request,
            Instant effectiveNow) {
        LoanRecord loan = assertSecuredLoan(loanRequestId);
        ensureCaseAssignedTo(staffUserId, loanRequestId);
        StaffSecuredProcedureResponse currentDetail = securedLoanProcedureRepository.findByLoanRequestId(loanRequestId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy hồ sơ vay thế chấp"));
        SecuredProcedureStatus status = request.status() != null
                ? request.status()
                : SecuredProcedureStatus.DRAFT;
        boolean finalizingBeforeContract = status == SecuredProcedureStatus.COMPLETED
                && (loan.status() == LoanStatus.APPOINTMENT_SCHEDULED || loan.status() == LoanStatus.APPROVED);
        boolean postContractPostAudit = status == SecuredProcedureStatus.COMPLETED
                && loan.status() == LoanStatus.CONTRACTED;
        if (loan.status() == LoanStatus.CONTRACTED && status != SecuredProcedureStatus.COMPLETED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Hồ sơ đã ký hợp đồng nên thủ tục thế chấp chỉ được giữ ở trạng thái hoàn tất");
        }
        if (status == SecuredProcedureStatus.COMPLETED && !finalizingBeforeContract && !postContractPostAudit) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Chỉ được hoàn tất thủ tục sau khi hồ sơ đã được lên lịch hẹn gặp mặt");
        }
        LoanApprovalReassessmentService.ReassessmentResult reassessment = null;
        if (finalizingBeforeContract) {
            validateCompletion(loan, currentDetail, request, effectiveNow);
            BigDecimal requestedAnnualRate = toAnnualRate(request.monthlyInterestRate());
            reassessment = loanApprovalReassessmentService.reassessAndPersist(
                    loan,
                    resolveVerifiedCustomer(loan.customerId()),
                    loan.approvedAmount(),
                    loan.approvedTermMonths(),
                    requestedAnnualRate,
                    request.appraisalValue(),
                    true);
            validateMonthlyPaymentMatchesDss(request, reassessment);
        }

        securedLoanProcedureRepository.upsert(loanRequestId, staffUserId, request);
        if (finalizingBeforeContract) {
            loanRepository.updateDecision(
                    loanRequestId,
                    LoanStatus.APPROVED,
                    buildCompletionReason(currentDetail, request, reassessment),
                    reassessment.eligibleLimit(),
                    reassessment.approvedAmount(),
                    reassessment.approvedTermMonths(),
                    reassessment.approvedAnnualRate(),
                    reassessment.approvedMonthlyPayment(),
                    reassessment.decisionPolicyVersion());
            loanStatusHistoryService.recordTransition(
                    loan,
                    LoanStatus.APPROVED,
                    staffUserId,
                    "SECURED_PROCEDURE_COMPLETION",
                    buildCompletionReason(currentDetail, request, reassessment));
            securedLoanProcedureRepository.markLatestAppointmentCompleted(loanRequestId);
            LoanRecord approvedLoan = loanRepository.findById(loanRequestId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Không tìm thấy hồ sơ vay"));
            loanContractService.createIfMissingFromApprovedLoan(
                    approvedLoan,
                    staffUserId,
                    buildContractScheduleTerms(request, reassessment),
                    LoanContractStatus.PENDING_ACCEPTANCE);
        }

        complianceAuditService.log(
                loan.customerId(),
                loan.id(),
                staffUserId,
                "SECURED_LOAN_PROCEDURE_" + status.name(),
                ComplianceOutcome.INFO,
                "secured procedure saved with status=" + status.name());

        return getSecuredProcedure(loanRequestId);
    }

    private LoanRecord assertSecuredLoan(Long loanRequestId) {
        LoanRecord loan = loanRepository.findById(loanRequestId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy hồ sơ vay"));
        if (loan.loanType() != LoanType.SECURED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Chỉ xử lý thủ tục này cho hồ sơ vay thế chấp");
        }
        return loan;
    }

    private void assertAppointmentManageable(LoanRecord loan) {
        if (loan.status() != LoanStatus.APPOINTMENT_SCHEDULED && loan.status() != LoanStatus.APPROVED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Chỉ hồ sơ thế chấp đang ở bước lịch hẹn hoặc đối chiếu tài sản mới được phép cập nhật lịch hẹn");
        }
    }

    private void ensureCaseAssignedTo(Long staffUserId, Long loanRequestId) {
        int updated = loanRepository.assignCaseIfUnassignedOrOwned(loanRequestId, staffUserId);
        if (updated > 0) {
            return;
        }
        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Hồ sơ vay thế chấp này đang được nhân viên khác phụ trách. Bạn không thể chỉnh sửa khi chưa được bàn giao.");
    }

    private void validateCompletion(
            LoanRecord loan,
            StaffSecuredProcedureResponse currentDetail,
            StaffSecuredProcedureRequest request,
            Instant effectiveNow) {
        if (loan.status() != LoanStatus.APPOINTMENT_SCHEDULED && loan.status() != LoanStatus.APPROVED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Chỉ hoàn tất thủ tục sau khi hồ sơ đã được lên lịch hẹn gặp mặt");
        }
        if (currentDetail.appointment() == null || currentDetail.appointment().scheduledAt() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Khoản vay thế chấp chưa có lịch hẹn gặp mặt hợp lệ");
        }
        if ("CANCELLED".equalsIgnoreCase(currentDetail.appointment().status())
                || "NO_SHOW".equalsIgnoreCase(currentDetail.appointment().status())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Lịch hẹn hiện tại đã bị hủy, không thể hoàn tất thủ tục");
        }
        if (currentDetail.appointment().scheduledAt().isAfter(effectiveNow.plusSeconds(60))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Chỉ được hoàn tất thủ tục sau khi buổi gặp mặt đã diễn ra");
        }

        List<String> missingFields = new ArrayList<>();
        addMissingIfBlank(request.contractNumber(), "Số hợp đồng", missingFields);
        addMissingIfNull(request.contractSignedDate(), "Ngày ký hợp đồng", missingFields);
        addMissingIfBlank(request.identityDocumentNumber(), "1.4 Số CMND/CCCD/Hộ chiếu", missingFields);
        addMissingIfBlank(request.permanentAddress(), "1.5 Địa chỉ hộ khẩu", missingFields);
        addMissingIfBlank(request.currentAddress(), "1.6 Địa chỉ nơi ở hiện tại", missingFields);
        addMissingIfBlank(request.assetType(), "3.1 Tài sản thế chấp", missingFields);
        addMissingIfBlank(request.assetManufacturer(), "3.2 Nhà sản xuất", missingFields);
        addMissingIfBlank(request.engineNumber(), "3.3 Số máy", missingFields);
        addMissingIfBlank(request.frameNumber(), "3.4 Số khung", missingFields);
        addMissingIfBlank(request.collateralOwnerName(), "Tên trên giấy đăng ký", missingFields);
        addMissingIfBlank(request.collateralIdentifier(), "Biển số / mã tài sản", missingFields);
        addMissingIfBlank(request.registrationNumber(), "Số giấy đăng ký", missingFields);
        addMissingIfNull(request.firstPaymentDate(), "4.5 Ngày thanh toán đầu tiên", missingFields);
        addMissingIfBlank(request.monthlyPaymentDay(), "4.6 Ngày thanh toán hằng tháng", missingFields);
        addMissingIfNull(request.finalPaymentDate(), "4.7 Ngày thanh toán cuối cùng", missingFields);
        addMissingIfBlank(request.appraisalReportCode(), "Mã biên bản thẩm định", missingFields);
        if (!missingFields.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Vui lòng nhập đầy đủ các trường chính của hợp đồng thế chấp: "
                            + String.join(", ", missingFields));
        }

        if (request.appraisalValue() == null || request.appraisalValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giá trị thẩm định tài sản phải lớn hơn 0");
        }
        if (request.monthlyInterestRate() == null || request.monthlyInterestRate().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lãi suất thực tế hằng tháng phải lớn hơn 0");
        }
        if (request.monthlyPaymentAmount() == null || request.monthlyPaymentAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khoản thanh toán hằng tháng phải lớn hơn 0");
        }
        if (request.contractSignedDate() != null
                && request.firstPaymentDate() != null
                && request.firstPaymentDate().isBefore(request.contractSignedDate())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Ngày thanh toán đầu tiên không được trước ngày ký hợp đồng");
        }
        if (request.firstPaymentDate() != null
                && request.finalPaymentDate() != null
                && request.finalPaymentDate().isBefore(request.firstPaymentDate())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Ngày thanh toán cuối cùng không được trước ngày thanh toán đầu tiên");
        }
        if (!Boolean.TRUE.equals(request.originalCertificateReceived())
                || !Boolean.TRUE.equals(request.certifiedCopyDelivered())
                || !Boolean.TRUE.equals(request.collateralRegistrationCompleted())
                || !Boolean.TRUE.equals(request.disputeChecked())
                || !Boolean.TRUE.equals(request.seizureNoticeAcknowledged())
                || !Boolean.TRUE.equals(request.documentsChecked())
                || !Boolean.TRUE.equals(request.assetInspected())
                || !Boolean.TRUE.equals(request.valuationApproved())
                || !Boolean.TRUE.equals(request.contractSigned())
                || !Boolean.TRUE.equals(request.collateralHandoverConfirmed())
                || !Boolean.TRUE.equals(request.disbursementReady())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Vui lòng xác nhận đủ các điều kiện về thủ tục pháp lý trước khi hoàn tất");
        }
    }

    private CustomerVerification resolveVerifiedCustomer(Long customerId) {
        CustomerVerification verification = customerVerificationService.getOrDefault(customerId);
        if (!verification.hasHardRejectFlag() && !isFullyVerified(verification)) {
            verification = customerInformationVerificationService
                    .syncLoanApprovalVerificationFromCurrentStatus(customerId);
        }
        if (verification.hasHardRejectFlag()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Không thể hoàn tất vay thế chấp vì khách hàng không đạt kiểm tra KYC/AML/gian lận");
        }
        if (!isFullyVerified(verification)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Không thể hoàn tất vay thế chấp trước khi tất cả bước xác minh đều đạt");
        }
        return verification;
    }

    private boolean isFullyVerified(CustomerVerification verification) {
        return verification.documentStatus() == VerificationStatus.PASSED
                && verification.identityStatus() == VerificationStatus.PASSED
                && verification.faceMatchStatus() == VerificationStatus.PASSED
                && verification.incomeStatus() == VerificationStatus.PASSED
                && verification.kycStatus() == VerificationStatus.PASSED
                && verification.amlStatus() == VerificationStatus.PASSED
                && !verification.fraudFlag();
    }

    private String buildCompletionReason(
            StaffSecuredProcedureResponse currentDetail,
            StaffSecuredProcedureRequest request,
            LoanApprovalReassessmentService.ReassessmentResult reassessment) {
        StringBuilder builder = new StringBuilder("Đã hoàn tất buổi gặp mặt và thẩm định tài sản trực tiếp");
        if (currentDetail.appointment() != null && currentDetail.appointment().scheduledAt() != null) {
            builder.append("; lịch hẹn=").append(currentDetail.appointment().scheduledAt());
        }
        if (request.appraisalValue() != null) {
            builder.append("; giá trị thẩm định=")
                    .append(request.appraisalValue().setScale(0, RoundingMode.HALF_UP).toPlainString());
        }
        if (reassessment.amountAdjusted()) {
            builder.append("; đã điều chỉnh số tiền duyệt theo hạn mức an toàn sau thẩm định");
        }
        return builder.toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void addMissingIfBlank(String value, String label, List<String> missingFields) {
        if (isBlank(value)) {
            missingFields.add(label);
        }
    }

    private void addMissingIfNull(Object value, String label, List<String> missingFields) {
        if (value == null) {
            missingFields.add(label);
        }
    }

    private BigDecimal toAnnualRate(BigDecimal monthlyInterestRate) {
        return monthlyInterestRate != null
                ? monthlyInterestRate.multiply(BigDecimal.valueOf(12)).setScale(6, RoundingMode.HALF_UP)
                : null;
    }

    private String buildAppointmentReason(Instant scheduledAt, String location, String note) {
        StringBuilder builder = new StringBuilder("Lịch hẹn gặp mặt: ").append(scheduledAt);
        if (!isBlank(location)) {
            builder.append("; địa điểm: ").append(location.trim());
        }
        if (!isBlank(note)) {
            builder.append("; ghi chú: ").append(note.trim());
        }
        return builder.toString();
    }

    private void validateMonthlyPaymentMatchesDss(
            StaffSecuredProcedureRequest request,
            LoanApprovalReassessmentService.ReassessmentResult reassessment) {
        if (request.monthlyPaymentAmount() == null || reassessment.approvedMonthlyPayment() == null) {
            return;
        }
        BigDecimal enteredPayment = request.monthlyPaymentAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal dssPayment = reassessment.approvedMonthlyPayment().setScale(2, RoundingMode.HALF_UP);
        if (enteredPayment.subtract(dssPayment).abs().compareTo(MONTHLY_PAYMENT_TOLERANCE) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Khoản thanh toán hằng tháng phải khớp kết quả DSS sau khi thẩm định lại: "
                            + dssPayment.toPlainString());
        }
    }

    private LoanContractScheduleTerms buildContractScheduleTerms(
            StaffSecuredProcedureRequest request,
            LoanApprovalReassessmentService.ReassessmentResult reassessment) {
        return new LoanContractScheduleTerms(
                request.contractSignedDate(),
                request.firstPaymentDate(),
                request.monthlyPaymentDay(),
                request.finalPaymentDate(),
                reassessment != null ? reassessment.approvedAnnualRate() : toAnnualRate(request.monthlyInterestRate()),
                reassessment != null ? reassessment.approvedMonthlyPayment() : request.monthlyPaymentAmount());
    }
}
