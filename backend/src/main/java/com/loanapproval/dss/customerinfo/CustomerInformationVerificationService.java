package com.loanapproval.dss.customerinfo;

import com.loanapproval.dss.creditcheck.CustomerCreditCheckService;
import com.loanapproval.dss.creditcheck.CustomerCreditCheckSummary;
import com.loanapproval.dss.compliance.ComplianceAuditService;
import com.loanapproval.dss.compliance.ComplianceOutcome;
import com.loanapproval.dss.customerinfo.dto.CustomerInformationVerificationResponse;
import com.loanapproval.dss.customerinfo.dto.ReviewCustomerInformationRequest;
import com.loanapproval.dss.customerinfo.dto.StaffCustomerInformationDetailResponse;
import com.loanapproval.dss.customerinfo.dto.StaffCustomerInformationSummaryResponse;
import com.loanapproval.dss.debt.CustomerDebtRepository;
import com.loanapproval.dss.loan.LoanApplicationSnapshotRepository;
import com.loanapproval.dss.notification.NotificationService;
import com.loanapproval.dss.profile.CustomerProfile;
import com.loanapproval.dss.profile.CustomerProfileRepository;
import com.loanapproval.dss.verification.CustomerVerification;
import com.loanapproval.dss.verification.CustomerVerificationRepository;
import com.loanapproval.dss.verification.VerificationStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CustomerInformationVerificationService {

    private final CustomerInformationVerificationRepository customerInformationVerificationRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final CustomerDebtRepository customerDebtRepository;
    private final CustomerVerificationRepository customerVerificationRepository;
    private final LoanApplicationSnapshotRepository loanApplicationSnapshotRepository;
    private final CustomerCreditCheckService customerCreditCheckService;
    private final ComplianceAuditService complianceAuditService;
    private final NotificationService notificationService;

    public CustomerInformationVerificationService(
        CustomerInformationVerificationRepository customerInformationVerificationRepository,
        CustomerProfileRepository customerProfileRepository,
        CustomerDebtRepository customerDebtRepository,
        CustomerVerificationRepository customerVerificationRepository,
        LoanApplicationSnapshotRepository loanApplicationSnapshotRepository,
        CustomerCreditCheckService customerCreditCheckService,
        ComplianceAuditService complianceAuditService,
        NotificationService notificationService
    ) {
        this.customerInformationVerificationRepository = customerInformationVerificationRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.customerDebtRepository = customerDebtRepository;
        this.customerVerificationRepository = customerVerificationRepository;
        this.loanApplicationSnapshotRepository = loanApplicationSnapshotRepository;
        this.customerCreditCheckService = customerCreditCheckService;
        this.complianceAuditService = complianceAuditService;
        this.notificationService = notificationService;
    }

    public CustomerInformationVerification getOrDefault(Long customerId) {
        return customerInformationVerificationRepository.findByCustomerId(customerId)
            .orElseGet(() -> new CustomerInformationVerification(
                customerId,
                VerificationStatus.PENDING,
                null,
                null,
                null,
                null,
                null
            ));
    }

    public CustomerInformationVerificationResponse getCurrentStatus(Long customerId) {
        return toResponse(getOrDefault(customerId));
    }

    public List<StaffCustomerInformationSummaryResponse> listCustomers(VerificationStatus status) {
        return customerInformationVerificationRepository.findCustomerSummaries(status);
    }

    public StaffCustomerInformationDetailResponse getCustomerDetail(Long customerId) {
        StaffCustomerInformationDetailResponse detail = customerInformationVerificationRepository.findCustomerDetailById(customerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy khách hàng"));
        CustomerCreditCheckSummary creditCheck = customerCreditCheckService.findLatestByCustomerId(customerId).orElse(null);

        return new StaffCustomerInformationDetailResponse(
            detail.customerId(),
            detail.email(),
            detail.registeredAt(),
            detail.status(),
            detail.rejectionReason(),
            detail.reviewedByEmail(),
            detail.reviewedAt(),
            withCreditCheck(detail.profile(), creditCheck)
        );
    }

    @Transactional
    public CustomerInformationVerificationResponse review(
        Long customerId,
        Long staffUserId,
        ReviewCustomerInformationRequest request
    ) {
        StaffCustomerInformationDetailResponse detail = customerInformationVerificationRepository.findCustomerDetailById(customerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy khách hàng"));

        VerificationStatus status = switch (request.action()) {
            case APPROVE -> VerificationStatus.PASSED;
            case REJECT -> VerificationStatus.FAILED;
        };

        if (!hasSubmittedProfile(detail.profile())) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Không thể xử lý xác minh khi khách hàng chưa nộp đầy đủ hồ sơ và phiếu lương"
            );
        }

        String reason = sanitizeReason(request.reason());
        if (status == VerificationStatus.FAILED && reason == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cần nhập lý do từ chối");
        }
        if (status == VerificationStatus.PASSED) {
            reason = null;
            if (request.verifiedMonthlyIncome() == null || request.verifiedMonthlyIncome().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cần nhập thu nhập đã xác minh lớn hơn 0 trước khi chấp thuận hồ sơ"
                );
            }
            customerProfileRepository.updateVerifiedMonthlyIncome(
                customerId, request.verifiedMonthlyIncome());
            customerDebtRepository.markPendingAsVerified(customerId, staffUserId, "Đã xác minh cùng hồ sơ thông tin");
            recalculateCustomerDti(customerId);
        } else {
            customerDebtRepository.markPendingAsRejected(customerId, staffUserId, reason);
        }

        customerInformationVerificationRepository.upsertDecision(
            customerId,
            status,
            reason,
            staffUserId,
            Instant.now()
        );
        syncLoanApprovalVerification(
            customerId,
            staffUserId,
            status,
            reason,
            status == VerificationStatus.PASSED ? request.verifiedMonthlyIncome() : null
        );

        complianceAuditService.log(
            customerId,
            null,
            staffUserId,
            status == VerificationStatus.PASSED
                ? "CUSTOMER_INFORMATION_VERIFICATION_APPROVED"
                : "CUSTOMER_INFORMATION_VERIFICATION_REJECTED",
            status == VerificationStatus.PASSED ? ComplianceOutcome.PASSED : ComplianceOutcome.FAILED,
            status == VerificationStatus.PASSED
                ? "customer information approved"
                : "customer information rejected: " + reason
        );
        notificationService.notifyCustomerInformationReviewCompleted(customerId, staffUserId, status, reason);

        return toResponse(getOrDefault(customerId));
    }

    @Transactional
    public void markPending(Long customerId) {
        markPending(customerId, null);
    }

    @Transactional
    public void markPending(Long customerId, BigDecimal preservedVerifiedMonthlyIncome) {
        customerInformationVerificationRepository.markPending(customerId);
        syncPendingLoanApprovalVerification(customerId, preservedVerifiedMonthlyIncome);
        notificationService.notifyStaffInformationReviewSubmitted(customerId);
    }

    @Transactional
    public CustomerVerification syncLoanApprovalVerificationFromCurrentStatus(Long customerId) {
        CustomerInformationVerification verification = getOrDefault(customerId);
        if (verification.status() == VerificationStatus.PASSED || verification.status() == VerificationStatus.FAILED) {
            syncLoanApprovalVerification(
                customerId,
                verification.reviewedBy(),
                verification.status(),
                verification.rejectionReason(),
                findVerifiedMonthlyIncome(customerId)
            );
        }
        return customerVerificationRepository.findByCustomerId(customerId)
            .orElseGet(() -> customerVerificationRepository.defaultPending(customerId));
    }

    @Transactional
    public void syncFromLoanApprovalVerification(
        Long customerId,
        Long staffUserId,
        CustomerVerification verification
    ) {
        CustomerProfile profile = customerProfileRepository.findByUserId(customerId).orElse(null);
        if (!hasSubmittedProfile(profile)) {
            customerInformationVerificationRepository.markPending(customerId);
            return;
        }

        VerificationStatus infoStatus = resolveInformationStatusFromLoanVerification(verification);
        if (infoStatus == VerificationStatus.PENDING) {
            customerInformationVerificationRepository.markPending(customerId);
            return;
        }

        String reason = infoStatus == VerificationStatus.FAILED
            ? buildReverseSyncedReason(verification)
            : null;
        customerInformationVerificationRepository.upsertDecision(
            customerId,
            infoStatus,
            reason,
            staffUserId,
            Instant.now()
        );
    }

    private CustomerInformationVerificationResponse toResponse(CustomerInformationVerification verification) {
        return new CustomerInformationVerificationResponse(
            verification.status(),
            verification.rejectionReason(),
            verification.reviewedAt()
        );
    }

    private String sanitizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        return reason.trim();
    }

    private void recalculateCustomerDti(Long customerId) {
        BigDecimal income = customerProfileRepository.findEffectiveMonthlyIncomeByUserId(customerId).orElse(null);
        if (income == null || income.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal activeDebt = customerDebtRepository.sumActiveMonthlyDebt(customerId);
        BigDecimal dti = activeDebt
            .multiply(BigDecimal.valueOf(100))
            .divide(income, 2, RoundingMode.HALF_UP);
        customerProfileRepository.updateDebtToIncomeRatio(customerId, dti);
    }

    private void syncLoanApprovalVerification(
        Long customerId,
        Long staffUserId,
        VerificationStatus status,
        String reason,
        BigDecimal verifiedMonthlyIncome
    ) {
        CustomerVerification current = customerVerificationRepository.findByCustomerId(customerId)
            .orElseGet(() -> customerVerificationRepository.defaultPending(customerId));
        Instant syncedAt = Instant.now();
        CustomerVerification synced = new CustomerVerification(
            customerId,
            documentLikeStatusFromInformation(current.documentStatus(), status),
            documentLikeStatusFromInformation(current.identityStatus(), status),
            current.faceMatchStatus(),
            incomeStatusFromInformation(status),
            documentLikeStatusFromInformation(current.kycStatus(), status),
            documentLikeStatusFromInformation(current.amlStatus(), status),
            current.fraudFlag(),
            buildSyncedVerificationNote(status, reason),
            staffUserId,
            syncedAt,
            current.createdAt(),
            syncedAt
        );

        customerVerificationRepository.upsert(synced);
        loanApplicationSnapshotRepository.syncCustomerInformationVerification(
            customerId,
            status,
            synced,
            verifiedMonthlyIncome,
            synced.note(),
            staffUserId,
            syncedAt
        );
    }

    private void syncPendingLoanApprovalVerification(Long customerId, BigDecimal verifiedMonthlyIncome) {
        CustomerVerification current = customerVerificationRepository.findByCustomerId(customerId)
            .orElseGet(() -> customerVerificationRepository.defaultPending(customerId));
        Instant syncedAt = Instant.now();
        CustomerVerification synced = new CustomerVerification(
            customerId,
            VerificationStatus.PENDING,
            VerificationStatus.PENDING,
            current.faceMatchStatus(),
            VerificationStatus.PENDING,
            VerificationStatus.PENDING,
            VerificationStatus.PENDING,
            current.fraudFlag(),
            "Chờ xác minh lại do hồ sơ khách hàng vừa được cập nhật",
            null,
            null,
            current.createdAt(),
            syncedAt
        );

        customerVerificationRepository.upsert(synced);
        loanApplicationSnapshotRepository.syncCustomerInformationVerification(
            customerId,
            VerificationStatus.PENDING,
            synced,
            verifiedMonthlyIncome,
            synced.note(),
            null,
            null
        );
    }

    private VerificationStatus documentLikeStatusFromInformation(
        VerificationStatus currentStatus,
        VerificationStatus informationStatus
    ) {
        if (informationStatus == VerificationStatus.PASSED) {
            return VerificationStatus.PASSED;
        }
        if (informationStatus == VerificationStatus.PENDING) {
            return VerificationStatus.PENDING;
        }
        return currentStatus != null ? currentStatus : VerificationStatus.PENDING;
    }

    private VerificationStatus incomeStatusFromInformation(VerificationStatus informationStatus) {
        if (informationStatus == VerificationStatus.PASSED) {
            return VerificationStatus.PASSED;
        }
        if (informationStatus == VerificationStatus.FAILED) {
            return VerificationStatus.FAILED;
        }
        return VerificationStatus.PENDING;
    }

    private BigDecimal findVerifiedMonthlyIncome(Long customerId) {
        return customerProfileRepository.findByUserId(customerId)
            .map(CustomerProfile::verifiedMonthlyIncome)
            .orElse(null);
    }

    private String buildSyncedVerificationNote(VerificationStatus status, String reason) {
        if (status == VerificationStatus.PASSED) {
            return "Đồng bộ từ bước xác minh thông tin";
        }
        if (reason == null || reason.isBlank()) {
            return "Từ chối ở bước xác minh thông tin";
        }
        return "Từ chối ở bước xác minh thông tin: " + reason;
    }

    private VerificationStatus resolveInformationStatusFromLoanVerification(CustomerVerification verification) {
        if (verification == null) {
            return VerificationStatus.PENDING;
        }
        if (verification.fraudFlag()
            || verification.kycStatus() == VerificationStatus.FAILED
            || verification.amlStatus() == VerificationStatus.FAILED
            || verification.incomeStatus() == VerificationStatus.FAILED) {
            return VerificationStatus.FAILED;
        }
        if (verification.incomeStatus() == VerificationStatus.PASSED
            && verification.kycStatus() == VerificationStatus.PASSED
            && verification.amlStatus() == VerificationStatus.PASSED) {
            return VerificationStatus.PASSED;
        }
        return VerificationStatus.PENDING;
    }

    private String buildReverseSyncedReason(CustomerVerification verification) {
        if (verification == null) {
            return "Từ chối ở bước xác minh tổng hợp";
        }
        if (verification.fraudFlag()) {
            return "Từ chối do cờ gian lận trong bước xác minh tổng hợp";
        }
        if (verification.kycStatus() == VerificationStatus.FAILED) {
            return "Từ chối do KYC không đạt trong bước xác minh tổng hợp";
        }
        if (verification.amlStatus() == VerificationStatus.FAILED) {
            return "Từ chối do AML không đạt trong bước xác minh tổng hợp";
        }
        if (verification.incomeStatus() == VerificationStatus.FAILED) {
            return "Từ chối do thu nhập không đạt trong bước xác minh tổng hợp";
        }
        if (verification.note() != null && !verification.note().isBlank()) {
            return verification.note().trim();
        }
        return "Từ chối ở bước xác minh tổng hợp";
    }

    private boolean hasSubmittedProfile(StaffCustomerInformationDetailResponse.ProfileSummary profile) {
        return profile != null
            && profile.identityNumber() != null
            && !profile.identityNumber().isBlank()
            && profile.payslipFileName() != null
            && !profile.payslipFileName().isBlank()
            && profile.identityCardFrontFileName() != null
            && !profile.identityCardFrontFileName().isBlank()
            && profile.identityCardBackFileName() != null
            && !profile.identityCardBackFileName().isBlank();
    }

    private boolean hasSubmittedProfile(CustomerProfile profile) {
        return profile != null
            && profile.identityNumber() != null
            && !profile.identityNumber().isBlank()
            && profile.payslipOriginalFilename() != null
            && !profile.payslipOriginalFilename().isBlank()
            && profile.identityCardFrontOriginalFilename() != null
            && !profile.identityCardFrontOriginalFilename().isBlank()
            && profile.identityCardBackOriginalFilename() != null
            && !profile.identityCardBackOriginalFilename().isBlank();
    }

    private StaffCustomerInformationDetailResponse.ProfileSummary withCreditCheck(
        StaffCustomerInformationDetailResponse.ProfileSummary profile,
        CustomerCreditCheckSummary creditCheck
    ) {
        if (profile == null) {
            return null;
        }
        return new StaffCustomerInformationDetailResponse.ProfileSummary(
            profile.fullName(),
            profile.phone(),
            profile.identityNumber(),
            profile.dateOfBirth(),
            profile.monthlyIncome(),
            profile.verifiedMonthlyIncome(),
            profile.debtToIncomeRatio(),
            profile.bankAccountNumber(),
            profile.bankName(),
            profile.creditHistoryScore(),
            profile.paymentRating(),
            creditCheck,
            profile.payslipFileName(),
            profile.payslipFileSize(),
            profile.payslipUploadedAt(),
            profile.identityCardFrontFileName(),
            profile.identityCardFrontFileSize(),
            profile.identityCardFrontUploadedAt(),
            profile.identityCardBackFileName(),
            profile.identityCardBackFileSize(),
            profile.identityCardBackUploadedAt()
        );
    }
}
