package com.loanapproval.dss.staff;

import com.loanapproval.dss.compliance.ComplianceAuditService;
import com.loanapproval.dss.compliance.ComplianceOutcome;
import com.loanapproval.dss.contract.LoanContractScheduleTerms;
import com.loanapproval.dss.contract.LoanContractService;
import com.loanapproval.dss.customerinfo.CustomerInformationVerificationService;
import com.loanapproval.dss.loan.LoanApprovalReassessmentService;
import com.loanapproval.dss.loan.LoanRecord;
import com.loanapproval.dss.loan.LoanRepository;
import com.loanapproval.dss.loan.LoanStatus;
import com.loanapproval.dss.loan.LoanType;
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

    private final SecuredLoanProcedureRepository securedLoanProcedureRepository;
    private final LoanRepository loanRepository;
    private final LoanContractService loanContractService;
    private final ComplianceAuditService complianceAuditService;
    private final CustomerInformationVerificationService customerInformationVerificationService;
    private final CustomerVerificationService customerVerificationService;
    private final LoanApprovalReassessmentService loanApprovalReassessmentService;

    public SecuredLoanProcedureService(
            SecuredLoanProcedureRepository securedLoanProcedureRepository,
            LoanRepository loanRepository,
            LoanContractService loanContractService,
            ComplianceAuditService complianceAuditService,
            CustomerInformationVerificationService customerInformationVerificationService,
            CustomerVerificationService customerVerificationService,
            LoanApprovalReassessmentService loanApprovalReassessmentService) {
        this.securedLoanProcedureRepository = securedLoanProcedureRepository;
        this.loanRepository = loanRepository;
        this.loanContractService = loanContractService;
        this.complianceAuditService = complianceAuditService;
        this.customerInformationVerificationService = customerInformationVerificationService;
        this.customerVerificationService = customerVerificationService;
        this.loanApprovalReassessmentService = loanApprovalReassessmentService;
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
    public StaffSecuredProcedureResponse saveSecuredProcedure(
            Long staffUserId,
            Long loanRequestId,
            StaffSecuredProcedureRequest request,
            Instant effectiveNow) {
        LoanRecord loan = assertSecuredLoan(loanRequestId);
        StaffSecuredProcedureResponse currentDetail = securedLoanProcedureRepository.findByLoanRequestId(loanRequestId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy hồ sơ vay thế chấp"));
        SecuredProcedureStatus status = request.status() != null
                ? request.status()
                : SecuredProcedureStatus.DRAFT;
        LoanApprovalReassessmentService.ReassessmentResult reassessment = null;
        if (status == SecuredProcedureStatus.COMPLETED) {
            validateCompletion(loan, currentDetail, request, effectiveNow);
            reassessment = loanApprovalReassessmentService.reassessAndPersist(
                    loan,
                    resolveVerifiedCustomer(loan.customerId()),
                    loan.approvedAmount(),
                    loan.approvedTermMonths(),
                    loan.approvedAnnualRate(),
                    request.appraisalValue(),
                    true);
        }

        securedLoanProcedureRepository.upsert(loanRequestId, staffUserId, request);
        if (status == SecuredProcedureStatus.COMPLETED) {
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
            securedLoanProcedureRepository.markLatestAppointmentCompleted(loanRequestId);
            LoanRecord approvedLoan = loanRepository.findById(loanRequestId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Không tìm thấy hồ sơ vay"));
            loanContractService.createIfMissingFromApprovedLoan(
                    approvedLoan,
                    staffUserId,
                    buildContractScheduleTerms(request));
            loanRepository.updateStatus(loanRequestId, LoanStatus.CONTRACTED);
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
        if ("CANCELLED".equalsIgnoreCase(currentDetail.appointment().status())) {
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

        if (request.salePrice() == null || request.salePrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giá bán tài sản phải lớn hơn 0");
        }
        if (request.downPayment() == null || request.downPayment().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khoản tiền mặt trả trước không hợp lệ");
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

    private LoanContractScheduleTerms buildContractScheduleTerms(StaffSecuredProcedureRequest request) {
        BigDecimal annualInterestRate = request.monthlyInterestRate() != null
                ? request.monthlyInterestRate().multiply(BigDecimal.valueOf(12)).setScale(6, RoundingMode.HALF_UP)
                : null;
        return new LoanContractScheduleTerms(
                request.contractSignedDate(),
                request.firstPaymentDate(),
                request.monthlyPaymentDay(),
                request.finalPaymentDate(),
                annualInterestRate,
                request.monthlyPaymentAmount());
    }
}
