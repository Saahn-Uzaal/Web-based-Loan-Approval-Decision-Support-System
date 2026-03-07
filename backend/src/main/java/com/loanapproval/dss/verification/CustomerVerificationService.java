package com.loanapproval.dss.verification;

import com.loanapproval.dss.compliance.ComplianceAuditService;
import com.loanapproval.dss.compliance.ComplianceOutcome;
import com.loanapproval.dss.verification.dto.CustomerVerificationResponse;
import com.loanapproval.dss.verification.dto.UpdateCustomerVerificationRequest;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerVerificationService {

    private final CustomerVerificationRepository customerVerificationRepository;
    private final ComplianceAuditService complianceAuditService;

    public CustomerVerificationService(
        CustomerVerificationRepository customerVerificationRepository,
        ComplianceAuditService complianceAuditService
    ) {
        this.customerVerificationRepository = customerVerificationRepository;
        this.complianceAuditService = complianceAuditService;
    }

    public CustomerVerification getOrDefault(Long customerId) {
        return customerVerificationRepository.findByCustomerId(customerId)
            .orElseGet(() -> customerVerificationRepository.defaultPending(customerId));
    }

    public CustomerVerificationResponse getByCustomerId(Long customerId) {
        return toResponse(getOrDefault(customerId));
    }

    @Transactional
    public CustomerVerificationResponse upsert(
        Long customerId,
        Long staffUserId,
        UpdateCustomerVerificationRequest request
    ) {
        CustomerVerification current = getOrDefault(customerId);
        CustomerVerification updated = new CustomerVerification(
            customerId,
            pick(request.documentStatus(), current.documentStatus()),
            pick(request.identityStatus(), current.identityStatus()),
            pick(request.incomeStatus(), current.incomeStatus()),
            pick(request.kycStatus(), current.kycStatus()),
            pick(request.amlStatus(), current.amlStatus()),
            request.fraudFlag() != null ? request.fraudFlag() : current.fraudFlag(),
            sanitizeNote(request.note()),
            staffUserId,
            Instant.now(),
            current.createdAt(),
            Instant.now()
        );
        customerVerificationRepository.upsert(updated);

        ComplianceOutcome outcome = updated.hasHardRejectFlag()
            ? ComplianceOutcome.FAILED
            : updated.isPending() ? ComplianceOutcome.INFO : ComplianceOutcome.PASSED;

        complianceAuditService.log(
            customerId,
            null,
            staffUserId,
            "VERIFICATION_UPDATE",
            outcome,
            buildVerificationDetails(updated)
        );

        return toResponse(updated);
    }

    private CustomerVerificationResponse toResponse(CustomerVerification verification) {
        return new CustomerVerificationResponse(
            verification.customerId(),
            verification.documentStatus(),
            verification.identityStatus(),
            verification.incomeStatus(),
            verification.kycStatus(),
            verification.amlStatus(),
            verification.fraudFlag(),
            verification.note(),
            verification.verifiedBy(),
            verification.verifiedAt(),
            verification.hasHardRejectFlag(),
            verification.isPending()
        );
    }

    private String sanitizeNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        return note.trim();
    }

    private VerificationStatus pick(VerificationStatus next, VerificationStatus fallback) {
        return next != null ? next : fallback;
    }

    private String buildVerificationDetails(CustomerVerification verification) {
        return String.format(
            "document=%s, identity=%s, income=%s, kyc=%s, aml=%s, fraud=%s",
            verification.documentStatus(),
            verification.identityStatus(),
            verification.incomeStatus(),
            verification.kycStatus(),
            verification.amlStatus(),
            verification.fraudFlag()
        );
    }
}
