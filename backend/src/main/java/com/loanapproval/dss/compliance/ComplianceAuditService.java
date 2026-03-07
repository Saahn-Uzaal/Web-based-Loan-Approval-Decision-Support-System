package com.loanapproval.dss.compliance;

import org.springframework.stereotype.Service;

@Service
public class ComplianceAuditService {

    private final ComplianceAuditLogRepository complianceAuditLogRepository;

    public ComplianceAuditService(ComplianceAuditLogRepository complianceAuditLogRepository) {
        this.complianceAuditLogRepository = complianceAuditLogRepository;
    }

    public void log(
        Long customerId,
        Long loanRequestId,
        Long actorUserId,
        String actionType,
        ComplianceOutcome outcome,
        String details
    ) {
        complianceAuditLogRepository.insert(
            customerId,
            loanRequestId,
            actorUserId,
            actionType,
            outcome,
            details
        );
    }
}
