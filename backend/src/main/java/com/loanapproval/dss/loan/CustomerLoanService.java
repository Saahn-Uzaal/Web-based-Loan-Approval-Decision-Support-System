package com.loanapproval.dss.loan;

import com.loanapproval.dss.compliance.ComplianceAuditService;
import com.loanapproval.dss.compliance.ComplianceOutcome;
import com.loanapproval.dss.contract.LoanContract;
import com.loanapproval.dss.contract.LoanContractService;
import com.loanapproval.dss.customerinfo.CustomerInformationVerificationService;
import com.loanapproval.dss.customerinfo.CustomerInformationVerification;
import com.loanapproval.dss.debt.CustomerDebtService;
import com.loanapproval.dss.dss.DecisionEngineService;
import com.loanapproval.dss.dss.DecisionInput;
import com.loanapproval.dss.dss.DssRecommendation;
import com.loanapproval.dss.dss.DssResult;
import com.loanapproval.dss.dss.DssResultRepository;
import com.loanapproval.dss.loan.dto.CreateLoanRequest;
import com.loanapproval.dss.loan.dto.LoanDetailResponse;
import com.loanapproval.dss.loan.dto.LoanDocumentResponse;
import com.loanapproval.dss.loan.dto.LoanSummaryResponse;
import com.loanapproval.dss.notification.NotificationService;
import com.loanapproval.dss.profile.CustomerProfile;
import com.loanapproval.dss.profile.CustomerProfileRepository;
import com.loanapproval.dss.repayment.LoanRepaymentSnapshot;
import com.loanapproval.dss.repayment.RepaymentScheduleService;
import com.loanapproval.dss.risk.RiskAssessment;
import com.loanapproval.dss.risk.RiskLevel;
import com.loanapproval.dss.risk.RiskAssessmentService;
import com.loanapproval.dss.shared.PageResponse;
import com.loanapproval.dss.verification.CustomerVerification;
import com.loanapproval.dss.verification.CustomerVerificationService;
import com.loanapproval.dss.verification.VerificationStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CustomerLoanService {

    private static final Logger log = LoggerFactory.getLogger(CustomerLoanService.class);
    private final LoanRepository loanRepository;
    private final LoanDocumentRepository loanDocumentRepository;
    private final LoanDocumentStorageService loanDocumentStorageService;
    private final CustomerProfileRepository customerProfileRepository;
    private final CustomerDebtService customerDebtService;
    private final DecisionEngineService decisionEngineService;
    private final DssResultRepository dssResultRepository;
    private final CustomerVerificationService customerVerificationService;
    private final RiskAssessmentService riskAssessmentService;
    private final ComplianceAuditService complianceAuditService;
    private final LoanContractService loanContractService;
    private final CustomerInformationVerificationService customerInformationVerificationService;
    private final LoanEligibilityService loanEligibilityService;
    private final RepaymentScheduleService repaymentScheduleService;
    private final NotificationService notificationService;
    private final LoanApplicationSnapshotRepository loanApplicationSnapshotRepository;
    private final LoanStatusHistoryService loanStatusHistoryService;
    private final LoanAppointmentRepository loanAppointmentRepository;

    public CustomerLoanService(
            LoanRepository loanRepository,
            LoanDocumentRepository loanDocumentRepository,
            LoanDocumentStorageService loanDocumentStorageService,
            CustomerProfileRepository customerProfileRepository,
            CustomerDebtService customerDebtService,
            DecisionEngineService decisionEngineService,
            DssResultRepository dssResultRepository,
            CustomerVerificationService customerVerificationService,
            RiskAssessmentService riskAssessmentService,
            ComplianceAuditService complianceAuditService,
            LoanContractService loanContractService,
            CustomerInformationVerificationService customerInformationVerificationService,
            LoanEligibilityService loanEligibilityService,
            RepaymentScheduleService repaymentScheduleService,
            NotificationService notificationService,
            LoanApplicationSnapshotRepository loanApplicationSnapshotRepository,
            LoanStatusHistoryService loanStatusHistoryService,
            LoanAppointmentRepository loanAppointmentRepository) {
        this.loanRepository = loanRepository;
        this.loanDocumentRepository = loanDocumentRepository;
        this.loanDocumentStorageService = loanDocumentStorageService;
        this.customerProfileRepository = customerProfileRepository;
        this.customerDebtService = customerDebtService;
        this.decisionEngineService = decisionEngineService;
        this.dssResultRepository = dssResultRepository;
        this.customerVerificationService = customerVerificationService;
        this.riskAssessmentService = riskAssessmentService;
        this.complianceAuditService = complianceAuditService;
        this.loanContractService = loanContractService;
        this.customerInformationVerificationService = customerInformationVerificationService;
        this.loanEligibilityService = loanEligibilityService;
        this.repaymentScheduleService = repaymentScheduleService;
        this.notificationService = notificationService;
        this.loanApplicationSnapshotRepository = loanApplicationSnapshotRepository;
        this.loanStatusHistoryService = loanStatusHistoryService;
        this.loanAppointmentRepository = loanAppointmentRepository;
    }

    @Transactional
    public LoanDetailResponse create(Long customerId, CreateLoanRequest request) {
        return create(customerId, request, LoanApplicationFiles.empty(), false);
    }

    @Transactional
    public LoanDetailResponse createDraft(Long customerId, CreateLoanRequest request) {
        assertNoOpenApplication(customerId);
        LoanType loanType = resolveLoanType(request.loanType());
        CollateralType collateralType = resolveCollateralType(loanType, request.collateralType());
        validateLoanRequest(loanType, request);
        String intakeNote = "Bản nháp hồ sơ vay đã được lưu. Vui lòng nộp đủ chứng từ trước khi gửi thẩm định.";
        LoanRecord loan = loanRepository.create(
                customerId,
                loanType,
                request.amount(),
                request.termMonths(),
                request.purpose(),
                collateralType,
                LoanStatus.DRAFT,
                null,
                intakeNote);
        loanStatusHistoryService.recordCreation(
                loan,
                customerId,
                "CUSTOMER_CREATE_DRAFT",
                "Customer created a draft loan application");
        return toDetailResponse(loan);
    }

    @Transactional
    public LoanDetailResponse create(Long customerId, CreateLoanRequest request, LoanApplicationFiles files) {
        return create(customerId, request, files, true);
    }

    private LoanDetailResponse create(
            Long customerId,
            CreateLoanRequest request,
            LoanApplicationFiles files,
            boolean requireDocuments) {
        assertNoOpenApplication(customerId);
        customerInformationVerificationService.assertApprovedForLoanCreation(customerId);
        LoanApplicationFiles safeFiles = files != null ? files : LoanApplicationFiles.empty();

        CustomerProfile profile = customerProfileRepository.findByUserId(customerId).orElse(null);
        LoanType loanType = resolveLoanType(request.loanType());
        CollateralType collateralType = resolveCollateralType(loanType, request.collateralType());
        validateLoanRequest(loanType, request);
        if (requireDocuments) {
            validateRequiredDocuments(loanType, safeFiles);
        }
        CustomerVerification verification = customerVerificationService.getOrDefault(customerId);
        CustomerInformationVerification informationVerification =
                customerInformationVerificationService.getOrDefault(customerId);
        BigDecimal existingMonthlyDebt = customerDebtService.sumActiveMonthlyDebt(customerId);
        BigDecimal projectedMonthlyPayment = loanContractService.calculateProjectedMonthlyPayment(
                loanType,
                request.amount(),
                request.termMonths());
        BigDecimal projectedDti = resolveProjectedDti(profile, existingMonthlyDebt, projectedMonthlyPayment);

        DecisionInput decisionInput = new DecisionInput(
                customerId,
                profile != null ? profile.effectiveMonthlyIncome() : null,
                projectedDti,
                profile != null ? profile.employmentStatus() : null,
                profile != null ? profile.dateOfBirth() : null,
                profile != null ? profile.employmentStartDate() : null,
                null,
                request.collateralValue(),
                existingMonthlyDebt,
                request.amount(),
                request.termMonths(),
                request.purpose(),
                profile != null ? profile.paymentRating() : null,
                isFailed(verification.kycStatus()),
                isFailed(verification.amlStatus()),
                verification.fraudFlag(),
                asIncomeVerified(verification.incomeStatus()),
                projectedMonthlyPayment);

        DssResult dssResult = decisionEngineService.evaluate(decisionInput);
        LoanEligibilityResult eligibility = loanEligibilityService.evaluate(
                profile,
                existingMonthlyDebt,
                loanType,
                request.amount(),
                request.termMonths(),
                request.collateralValue(),
                dssResult.riskRank());
        String intakeNote = buildIntakeNote(loanType);

        LoanRecord loan = loanRepository.create(
                customerId,
                loanType,
                request.amount(),
                request.termMonths(),
                request.purpose(),
                collateralType,
                eligibility.eligibleLimit(),
                intakeNote);
        if (loanType == LoanType.SECURED) {
            loanRepository.updateCollateralValue(loan.id(), request.collateralValue());
        }
        loanStatusHistoryService.recordCreation(
                loan,
                customerId,
                "CUSTOMER_SUBMIT_LOAN",
                "Customer submitted a new loan application");

        loanApplicationSnapshotRepository.createIfMissing(
                loan.id(),
                customerId,
                profile,
                existingMonthlyDebt,
                customerDebtService.countActiveDebts(customerId),
                informationVerification,
                verification);

        storeDocuments(loan.id(), loanType, safeFiles, false);

        log.info(
                "Loan application created: loanId={}, customerId={}, loanType={}, amount={}, termMonths={}, purpose={}",
                loan.id(), customerId, loanType, request.amount(), request.termMonths(), request.purpose());

        dssResultRepository.upsert(loan.id(), dssResult);

        RiskAssessment riskAssessment = riskAssessmentService.evaluateAndSave(
                loan.id(),
                decisionInput,
                dssResult,
                verification);

        applyWorkflowTransition(customerId, loan, dssResult, riskAssessment, verification);
        loan = loanRepository.findOwnedById(loan.id(), customerId).orElse(loan);

        complianceAuditService.log(
                customerId,
                loan.id(),
                customerId,
                "LOAN_APPLICATION_EVALUATED",
                resolveComplianceOutcome(verification),
                String.format(
                        "loanType=%s, recommendation=%s, riskLevel=%s, creditRisk=%d, fraudRisk=%d, operationalRisk=%d, projectedDti=%s, eligibleLimit=%s",
                        loanType,
                        dssResult.recommendation(),
                        riskAssessment.overallRiskLevel(),
                        riskAssessment.creditRiskScore(),
                        riskAssessment.fraudRiskScore(),
                        riskAssessment.operationalRiskScore(),
                        projectedDti != null ? projectedDti.toPlainString() : "N/A",
                        eligibility.eligibleLimit() != null ? eligibility.eligibleLimit().toPlainString() : "N/A"));
        if (loan.status() == LoanStatus.PENDING) {
            notificationService.notifyStaffLoanApplicationSubmitted(loan.id(), customerId, loan.loanType());
        }

        return toDetailResponse(loan);
    }

    public List<LoanSummaryResponse> listMine(Long customerId) {
        return loanRepository.findByCustomerId(customerId).stream()
                .map(loan -> toSummaryResponse(customerId, loan))
                .toList();
    }

    public PageResponse<LoanSummaryResponse> listMinePaged(Long customerId, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safeOffset = Math.max(page, 0) * safeSize;
        long total = loanRepository.countByCustomerId(customerId);
        List<LoanSummaryResponse> content = loanRepository
                .findByCustomerIdPaged(customerId, safeOffset, safeSize)
                .stream()
                .map(loan -> toSummaryResponse(customerId, loan))
                .toList();
        return PageResponse.of(content, Math.max(page, 0), safeSize, total);
    }

    public LoanDetailResponse getMineById(Long customerId, Long id) {
        LoanRecord loan = loanRepository.findOwnedById(id, customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ vay"));
        return toDetailResponse(loan);
    }

    @Transactional
    public LoanDetailResponse acceptApprovedLoan(Long customerId, Long id) {
        LoanRecord loan = loanRepository.findOwnedById(id, customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ vay"));
        Long assignedStaffUserId = loanRepository.findAssignedStaffUserId(id).orElse(null);
        if (loan.status() != LoanStatus.APPROVED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Chỉ có thể chấp nhận điều khoản khi hồ sơ đã được phê duyệt và chưa ký hợp đồng");
        }

        LoanContract contract = loanContractService.activateForCustomer(customerId, id);
        int updated = loanRepository.markAcceptedAndContracted(
                id,
                customerId,
                loan.decisionPolicyVersion() != null ? loan.decisionPolicyVersion() : loanEligibilityService.currentPolicyVersion());
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hồ sơ vay đã thay đổi trạng thái trong lúc xử lý");
        }
        loanStatusHistoryService.recordTransition(
                loan,
                LoanStatus.CONTRACTED,
                customerId,
                "CUSTOMER_ACCEPT_LOAN",
                "Customer accepted approved loan terms");
        complianceAuditService.log(
                customerId,
                id,
                customerId,
                "CUSTOMER_ACCEPT_LOAN_TERMS",
                ComplianceOutcome.PASSED,
                "customer reviewed and accepted loan contract #" + contract.id());
        notificationService.notifyStaffLoanContractAccepted(id, customerId, assignedStaffUserId, loan.loanType());
        return toDetailResponse(loanRepository.findOwnedById(id, customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ vay")));
    }

    @Transactional
    public LoanDetailResponse withdrawLoan(Long customerId, Long id) {
        LoanRecord loan = loanRepository.findOwnedById(id, customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ vay"));
        Long assignedStaffUserId = loanRepository.findAssignedStaffUserId(id).orElse(null);
        if (loan.status() == LoanStatus.CONTRACTED
                || loan.status() == LoanStatus.ACTIVE
                || loan.status() == LoanStatus.OVERDUE
                || loan.status() == LoanStatus.CLOSED
                || loan.status() == LoanStatus.REJECTED
                || loan.status() == LoanStatus.WITHDRAWN) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Không thể rút hồ sơ khi hồ sơ đã ký hợp đồng, giải ngân hoặc đã có kết quả cuối");
        }
        int updated = loanRepository.updateOwnedStatusAndReason(
                id,
                customerId,
                LoanStatus.WITHDRAWN,
                "Khách hàng đã rút hồ sơ trước khi ký hợp đồng");
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hồ sơ vay đã thay đổi trạng thái trong lúc xử lý");
        }
        loanStatusHistoryService.recordTransition(
                loan,
                LoanStatus.WITHDRAWN,
                customerId,
                "CUSTOMER_WITHDRAW_LOAN",
                "Customer withdrew loan application before contracting");
        complianceAuditService.log(
                customerId,
                id,
                customerId,
                "CUSTOMER_WITHDRAW_LOAN_APPLICATION",
                ComplianceOutcome.INFO,
                "customer withdrew application before contract");
        notificationService.notifyStaffLoanWithdrawn(id, customerId, assignedStaffUserId, loan.loanType());
        return toDetailResponse(loanRepository.findOwnedById(id, customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ vay")));
    }

    @Transactional
    public LoanDetailResponse resubmitLoan(Long customerId, Long id) {
        return resubmitLoan(customerId, id, LoanApplicationFiles.empty());
    }

    @Transactional
    public LoanDetailResponse resubmitLoan(Long customerId, Long id, LoanApplicationFiles files) {
        LoanRecord loan = loanRepository.findOwnedById(id, customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ vay"));
        if (loan.status() != LoanStatus.NEEDS_MORE_INFO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chỉ gửi lại hồ sơ khi nhân viên yêu cầu bổ sung");
        }
        LoanApplicationFiles safeFiles = files != null ? files : LoanApplicationFiles.empty();
        if (!hasSupplementalDocuments(safeFiles)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Vui lòng đính kèm ít nhất một giấy tờ bổ sung trước khi gửi lại hồ sơ");
        }
        storeSupplementalDocuments(id, safeFiles.supplementalDocuments());
        int updated = loanRepository.updateOwnedStatusAndReason(
                id,
                customerId,
                LoanStatus.PENDING,
                "Khách hàng đã gửi lại hồ sơ sau khi bổ sung thông tin");
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hồ sơ vay đã thay đổi trạng thái trong lúc xử lý");
        }
        loanStatusHistoryService.recordTransition(
                loan,
                LoanStatus.PENDING,
                customerId,
                "CUSTOMER_RESUBMIT_LOAN",
                "Customer resubmitted loan after providing additional documents");
        notificationService.notifyStaffLoanApplicationSubmitted(id, customerId, loan.loanType());
        return toDetailResponse(loanRepository.findOwnedById(id, customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ vay")));
    }

    public LoanDocumentDownload downloadDocument(Long customerId, Long loanRequestId, Long documentId) {
        loanRepository.findOwnedById(loanRequestId, customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ vay"));
        LoanDocumentRecord document = loanDocumentRepository.findByLoanRequestIdAndDocumentId(loanRequestId, documentId)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy chứng từ hồ sơ vay"));
        return loanDocumentStorageService.load(document);
    }

    private LoanType resolveLoanType(LoanType loanType) {
        return loanType != null ? loanType : LoanType.UNSECURED;
    }

    private CollateralType resolveCollateralType(LoanType loanType, CollateralType collateralType) {
        if (loanType == LoanType.SECURED) {
            return collateralType != null ? collateralType : CollateralType.VEHICLE_REGISTRATION;
        }
        return null;
    }

    private void validateRequiredDocuments(LoanType loanType, LoanApplicationFiles files) {
        if (loanType == LoanType.SECURED) {
            requireFile(files.vehicleRegistration(), "Vui lòng chụp hoặc tải ảnh giấy tờ xe");
            requireFile(files.licensePlateImage(), "Vui lòng chụp hoặc tải ảnh biển số xe");
            return;
        }
        requireFile(files.idCardFront(), "Vui lòng tải ảnh mặt trước CCCD");
        requireFile(files.idCardBack(), "Vui lòng tải ảnh mặt sau CCCD");
        requireFile(files.faceCapture(), "Vui lòng chụp ảnh khuôn mặt hiện tại");
    }

    private void requireFile(org.springframework.web.multipart.MultipartFile file, String message) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private void validateLoanRequest(LoanType loanType, CreateLoanRequest request) {
        if (loanType == LoanType.SECURED
                && (request.collateralValue() == null || request.collateralValue().compareTo(BigDecimal.ZERO) <= 0)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Vui lòng nhập giá trị tài sản bảo đảm hợp lệ");
        }
    }

    private String buildIntakeNote(LoanType loanType) {
        if (loanType == LoanType.SECURED) {
            return "Đã tiếp nhận yêu cầu vay thế chấp. Nhân viên sẽ liên hệ để đặt lịch hẹn và đối chiếu tài sản trực tiếp.";
        }
        return "Hồ sơ vay tín chấp đã được gửi thẩm định. Nhân viên sẽ đối chiếu CCCD, ảnh khuôn mặt, thông tin tín dụng nội bộ và DTI.";
    }

    private void storeDocuments(Long loanRequestId, LoanType loanType, LoanApplicationFiles files, boolean upsert) {
        if (files == null) {
            return;
        }
        if (loanType == LoanType.SECURED) {
            storeDocumentIfPresent(
                    loanRequestId,
                    LoanDocumentType.VEHICLE_REGISTRATION,
                    files.vehicleRegistration(),
                    upsert);
            storeDocumentIfPresent(
                    loanRequestId,
                    LoanDocumentType.LICENSE_PLATE_IMAGE,
                    files.licensePlateImage(),
                    upsert);
            return;
        }
        storeDocumentIfPresent(loanRequestId, LoanDocumentType.ID_CARD_FRONT, files.idCardFront(), upsert);
        storeDocumentIfPresent(loanRequestId, LoanDocumentType.ID_CARD_BACK, files.idCardBack(), upsert);
        storeDocumentIfPresent(loanRequestId, LoanDocumentType.FACE_CAPTURE, files.faceCapture(), upsert);
    }

    private void storeDocumentIfPresent(
            Long loanRequestId,
            LoanDocumentType documentType,
            org.springframework.web.multipart.MultipartFile file,
            boolean upsert) {
        if (file == null || file.isEmpty()) {
            return;
        }
        LoanDocumentStorageService.StoredLoanDocument stored = loanDocumentStorageService.store(loanRequestId,
                documentType, file);
        if (upsert) {
            loanDocumentRepository.upsert(loanRequestId, documentType, stored);
            return;
        }
        loanDocumentRepository.create(loanRequestId, documentType, stored);
    }

    private void storeSupplementalDocuments(Long loanRequestId, List<org.springframework.web.multipart.MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return;
        }
        files.stream()
                .filter(file -> file != null && !file.isEmpty())
                .forEach(file -> {
                    LoanDocumentStorageService.StoredLoanDocument stored = loanDocumentStorageService.store(
                            loanRequestId,
                            LoanDocumentType.SUPPLEMENTAL_DOCUMENT,
                            file);
                    loanDocumentRepository.create(loanRequestId, LoanDocumentType.SUPPLEMENTAL_DOCUMENT, stored);
                });
    }

    private void applyWorkflowTransition(
            Long customerId,
            LoanRecord loan,
            DssResult dssResult,
            RiskAssessment riskAssessment,
            CustomerVerification verification) {
        if (verification.hasHardRejectFlag()) {
            String reason = "Tự động từ chối do không đạt kiểm tra tuân thủ (KYC/AML/gian lận).";
            loanRepository.updateDecision(loan.id(), LoanStatus.REJECTED, reason, null, null, null, null, null);
            loanStatusHistoryService.recordTransition(
                    loan,
                    LoanStatus.REJECTED,
                    customerId,
                    "AUTO_DECISION",
                    reason);
            complianceAuditService.log(
                    customerId,
                    loan.id(),
                    customerId,
                    "LOAN_APPLICATION_AUTO_REJECTED",
                    ComplianceOutcome.FAILED,
                    reason);
            notificationService.notifyCustomerLoanDecisionUpdated(
                    loan.id(),
                    customerId,
                    null,
                    loan.loanType(),
                    LoanStatus.REJECTED,
                    reason,
                    true);
            return;
        }

        if (shouldAutoReject(dssResult, riskAssessment)) {
            String reason = buildAutoRejectReason(loan.loanType(), dssResult, riskAssessment);
            loanRepository.updateDecision(loan.id(), LoanStatus.REJECTED, reason, null, null, null, null, null);
            loanStatusHistoryService.recordTransition(
                    loan,
                    LoanStatus.REJECTED,
                    customerId,
                    "AUTO_DECISION",
                    reason);
            complianceAuditService.log(
                    customerId,
                    loan.id(),
                    customerId,
                    "LOAN_APPLICATION_AUTO_REJECTED",
                    ComplianceOutcome.FAILED,
                    reason);
            notificationService.notifyCustomerLoanDecisionUpdated(
                    loan.id(),
                    customerId,
                    null,
                    loan.loanType(),
                    LoanStatus.REJECTED,
                    reason,
                    true);
            return;
        }

    }

    private boolean shouldAutoReject(DssResult dssResult, RiskAssessment riskAssessment) {
        return dssResult.recommendation() == DssRecommendation.REJECT_RECOMMENDED
                || (riskAssessment != null && riskAssessment.overallRiskLevel() == RiskLevel.HIGH);
    }

    private String buildAutoRejectReason(LoanType loanType, DssResult dssResult, RiskAssessment riskAssessment) {
        if (loanType == LoanType.SECURED
                && dssResult.creditScore() != null
                && dssResult.creditScore() < 620) {
            return "Hồ sơ vay thế chấp đã bị tự động từ chối vì điểm tín dụng quá thấp nên hồ sơ đã bị hủy.";
        }
        boolean rejectRecommended = dssResult.recommendation() == DssRecommendation.REJECT_RECOMMENDED;
        boolean highRisk = riskAssessment != null && riskAssessment.overallRiskLevel() == RiskLevel.HIGH;
        if (rejectRecommended && highRisk) {
            return "Tự động từ chối do DSS đề xuất từ chối và mức rủi ro tổng thể ở ngưỡng HIGH.";
        }
        if (rejectRecommended) {
            return "Tự động từ chối do DSS đề xuất từ chối.";
        }
        return "Tự động từ chối do mức rủi ro tổng thể ở ngưỡng HIGH.";
    }

    private BigDecimal resolveProjectedDti(
            CustomerProfile profile,
            BigDecimal existingMonthlyDebt,
            BigDecimal projectedMonthlyPayment) {
        BigDecimal income = profile != null ? profile.effectiveMonthlyIncome() : null;
        if (income == null || income.compareTo(BigDecimal.ZERO) <= 0) {
            return profile != null ? profile.debtToIncomeRatio() : null;
        }
        BigDecimal totalMonthlyDebt = nonNegative(existingMonthlyDebt).add(nonNegative(projectedMonthlyPayment));
        return totalMonthlyDebt
                .multiply(BigDecimal.valueOf(100))
                .divide(income, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal nonNegative(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return value;
    }

    private boolean isFailed(VerificationStatus status) {
        return status == VerificationStatus.FAILED;
    }

    private Boolean asIncomeVerified(VerificationStatus status) {
        if (status == VerificationStatus.PASSED) {
            return true;
        }
        if (status == VerificationStatus.FAILED) {
            return false;
        }
        return null;
    }

    private ComplianceOutcome resolveComplianceOutcome(CustomerVerification verification) {
        if (verification.hasHardRejectFlag()) {
            return ComplianceOutcome.FAILED;
        }
        if (verification.isPending()) {
            return ComplianceOutcome.INFO;
        }
        return ComplianceOutcome.PASSED;
    }

    private LoanSummaryResponse toSummaryResponse(Long customerId, LoanRecord loan) {
        var contract = loanContractService.findByLoanRequestId(loan.id());
        LoanRepaymentSnapshot snapshot = supportsRepaymentSnapshot(loan, contract)
                ? repaymentScheduleService.snapshot(loan, contract, customerId)
                : null;
        return new LoanSummaryResponse(
                loan.id(),
                loan.loanType(),
                loan.amount(),
                loan.termMonths(),
                loan.purpose(),
                loan.status(),
                loan.finalReason(),
                loan.approvedAmount(),
                loan.approvedMonthlyPayment(),
                contract != null ? contract.totalInterest() : null,
                snapshot != null ? snapshot.totalRepayable() : null,
                snapshot != null ? snapshot.totalPaid() : null,
                snapshot != null ? snapshot.outstandingAmount() : null,
                snapshot != null ? snapshot.currentAmountDue() : null,
                snapshot != null ? snapshot.installmentNumber() : null,
                snapshot != null ? snapshot.dueDate() : null,
                snapshot != null ? snapshot.overdue() : null,
                snapshot != null ? snapshot.overdueDays() : null,
                loan.createdAt());
    }

    private boolean supportsRepaymentSnapshot(LoanRecord loan, LoanContract contract) {
        if (contract == null) {
            return false;
        }
        return loan.status() == LoanStatus.ACTIVE
                || loan.status() == LoanStatus.OVERDUE
                || loan.status() == LoanStatus.CLOSED;
    }

    private LoanDetailResponse toDetailResponse(LoanRecord loan) {
        return new LoanDetailResponse(
                loan.id(),
                loan.customerId(),
                loan.loanType(),
                loan.amount(),
                loan.termMonths(),
                loan.purpose(),
                loan.collateralType(),
                loan.status(),
                loan.finalReason(),
                loan.eligibleLimit(),
                loan.approvedAmount(),
                loan.approvedTermMonths(),
                loan.approvedAnnualRate(),
                loan.approvedMonthlyPayment(),
                loan.decisionPolicyVersion(),
                loan.intakeNote(),
                loanAppointmentRepository.findLatestByLoanRequestId(loan.id()).orElse(null),
                loanDocumentRepository.findByLoanRequestId(loan.id()).stream()
                        .map(this::toDocumentResponse)
                        .toList(),
                loan.createdAt(),
                loan.updatedAt());
    }

    private LoanDocumentResponse toDocumentResponse(LoanDocumentRecord document) {
        return new LoanDocumentResponse(
                document.id(),
                document.documentType(),
                document.originalFileName(),
                document.fileSize(),
                document.uploadedAt());
    }

    private boolean hasSupplementalDocuments(LoanApplicationFiles files) {
        return files.supplementalDocuments() != null
                && files.supplementalDocuments().stream().anyMatch(file -> file != null && !file.isEmpty());
    }

    private void assertNoOpenApplication(Long customerId) {
        if (!loanRepository.existsOpenApplicationByCustomerId(customerId)) {
            return;
        }
        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Mỗi khách hàng chỉ được phép có 1 hồ sơ vay tại một thời điểm. Vui lòng hoàn tất hoặc rút hồ sơ hiện tại trước khi tạo mới.");
    }
}

