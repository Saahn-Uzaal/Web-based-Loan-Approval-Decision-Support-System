package com.loanapproval.dss.customerinfo;

import com.loanapproval.dss.compliance.ComplianceAuditService;
import com.loanapproval.dss.compliance.ComplianceOutcome;
import com.loanapproval.dss.customerinfo.dto.CustomerInformationVerificationResponse;
import com.loanapproval.dss.customerinfo.dto.ReviewCustomerInformationRequest;
import com.loanapproval.dss.customerinfo.dto.StaffCustomerInformationDetailResponse;
import com.loanapproval.dss.customerinfo.dto.StaffCustomerInformationSummaryResponse;
import com.loanapproval.dss.debt.CustomerDebtRepository;
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
    private final ComplianceAuditService complianceAuditService;

    public CustomerInformationVerificationService(
        CustomerInformationVerificationRepository customerInformationVerificationRepository,
        CustomerProfileRepository customerProfileRepository,
        CustomerDebtRepository customerDebtRepository,
        CustomerVerificationRepository customerVerificationRepository,
        ComplianceAuditService complianceAuditService
    ) {
        this.customerInformationVerificationRepository = customerInformationVerificationRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.customerDebtRepository = customerDebtRepository;
        this.customerVerificationRepository = customerVerificationRepository;
        this.complianceAuditService = complianceAuditService;
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

        List<StaffCustomerInformationDetailResponse.DebtItem> debts = customerDebtRepository.findByCustomerId(customerId)
            .stream()
            .map(debt -> new StaffCustomerInformationDetailResponse.DebtItem(
                debt.id(),
                debt.debtType(),
                debt.monthlyPayment(),
                debt.remainingBalance(),
                debt.lenderName(),
                debt.status().name()
            ))
            .toList();

        return new StaffCustomerInformationDetailResponse(
            detail.customerId(),
            detail.email(),
            detail.registeredAt(),
            detail.status(),
            detail.rejectionReason(),
            detail.reviewedByEmail(),
            detail.reviewedAt(),
            detail.profile(),
            debts
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

        if (status == VerificationStatus.PASSED && detail.profile() == null) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Không thể chấp thuận thông tin khách hàng trước khi hồ sơ được hoàn thiện"
            );
        }
        if (status == VerificationStatus.PASSED &&
            (detail.profile().payslipFileName() == null || detail.profile().payslipFileName().isBlank())) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Không thể chấp thuận hồ sơ nếu khách hàng chưa nộp phiếu lương"
            );
        }

        String reason = sanitizeReason(request.reason());
        if (status == VerificationStatus.FAILED && reason == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cần nhập lý do từ chối");
        }
        if (status == VerificationStatus.PASSED) {
            reason = null;
            if (request.verifiedMonthlyIncome() != null) {
                customerProfileRepository.updateVerifiedMonthlyIncome(
                    customerId, request.verifiedMonthlyIncome());
            }
            recalculateCustomerDti(customerId);
        }

        customerInformationVerificationRepository.upsertDecision(
            customerId,
            status,
            reason,
            staffUserId,
            Instant.now()
        );
        syncLoanApprovalVerification(customerId, staffUserId, status, reason);

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

        return toResponse(getOrDefault(customerId));
    }

    @Transactional
    public void markPending(Long customerId) {
        customerInformationVerificationRepository.markPending(customerId);
        syncPendingLoanApprovalVerification(customerId);
    }

    public void assertApprovedForLoanCreation(Long customerId) {
        CustomerInformationVerification verification = getOrDefault(customerId);
        if (verification.status() == VerificationStatus.PASSED) {
            return;
        }

        if (verification.status() == VerificationStatus.FAILED) {
            String reason = verification.rejectionReason();
            String message = reason == null || reason.isBlank()
                ? "Thông tin kê khai của bạn đã bị từ chối. Vui lòng cập nhật hồ sơ và chờ nhân viên chấp thuận trước khi tạo hồ sơ vay."
                : "Thông tin kê khai của bạn đã bị từ chối: " + reason;
            throw new ResponseStatusException(HttpStatus.CONFLICT, message);
        }

        throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Thông tin kê khai của bạn đang chờ nhân viên xác minh. Vui lòng chờ chấp thuận trước khi tạo hồ sơ vay."
        );
    }

    @Transactional
    public CustomerVerification syncLoanApprovalVerificationFromCurrentStatus(Long customerId) {
        CustomerInformationVerification verification = getOrDefault(customerId);
        if (verification.status() == VerificationStatus.PASSED || verification.status() == VerificationStatus.FAILED) {
            syncLoanApprovalVerification(
                customerId,
                verification.reviewedBy(),
                verification.status(),
                verification.rejectionReason()
            );
        }
        return customerVerificationRepository.findByCustomerId(customerId)
            .orElseGet(() -> customerVerificationRepository.defaultPending(customerId));
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
        String reason
    ) {
        CustomerVerification current = customerVerificationRepository.findByCustomerId(customerId)
            .orElseGet(() -> customerVerificationRepository.defaultPending(customerId));

        customerVerificationRepository.upsert(new CustomerVerification(
            customerId,
            current.documentStatus(),
            current.identityStatus(),
            current.faceMatchStatus(),
            status == VerificationStatus.PASSED ? VerificationStatus.PASSED : VerificationStatus.FAILED,
            current.kycStatus(),
            current.amlStatus(),
            current.fraudFlag(),
            buildSyncedVerificationNote(status, reason),
            staffUserId,
            Instant.now(),
            current.createdAt(),
            Instant.now()
        ));
    }

    private void syncPendingLoanApprovalVerification(Long customerId) {
        CustomerVerification current = customerVerificationRepository.findByCustomerId(customerId)
            .orElseGet(() -> customerVerificationRepository.defaultPending(customerId));

        customerVerificationRepository.upsert(new CustomerVerification(
            customerId,
            current.documentStatus(),
            current.identityStatus(),
            current.faceMatchStatus(),
            VerificationStatus.PENDING,
            current.kycStatus(),
            current.amlStatus(),
            current.fraudFlag(),
            "Chờ xác minh lại do hồ sơ khách hàng vừa được cập nhật",
            null,
            null,
            current.createdAt(),
            Instant.now()
        ));
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
}
