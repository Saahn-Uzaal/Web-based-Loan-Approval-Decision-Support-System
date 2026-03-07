package com.loanapproval.dss.compliance;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ComplianceAuditLogRepository {

    private final JdbcTemplate jdbcTemplate;

    public ComplianceAuditLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(
        Long customerId,
        Long loanRequestId,
        Long actorUserId,
        String actionType,
        ComplianceOutcome outcome,
        String details
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO compliance_audit_logs (
                customer_id,
                loan_request_id,
                actor_user_id,
                action_type,
                outcome,
                details
            ) VALUES (?, ?, ?, ?, ?, ?)
            """,
            customerId,
            loanRequestId,
            actorUserId,
            actionType,
            outcome.name(),
            details
        );
    }
}
