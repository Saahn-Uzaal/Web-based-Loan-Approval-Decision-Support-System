package com.loanapproval.dss.loan;

import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LoanStatusHistoryRepository {

    private final JdbcTemplate jdbcTemplate;

    public LoanStatusHistoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void create(
            Long loanRequestId,
            LoanStatus fromStatus,
            LoanStatus toStatus,
            String changeReason,
            Long changedByUserId,
            String source) {
        jdbcTemplate.update(
                """
                INSERT INTO loan_status_history (
                    loan_request_id,
                    from_status,
                    to_status,
                    change_reason,
                    changed_by_user_id,
                    source,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                loanRequestId,
                fromStatus != null ? fromStatus.name() : null,
                toStatus.name(),
                changeReason,
                changedByUserId,
                source,
                Timestamp.from(java.time.Instant.now()));
    }
}
