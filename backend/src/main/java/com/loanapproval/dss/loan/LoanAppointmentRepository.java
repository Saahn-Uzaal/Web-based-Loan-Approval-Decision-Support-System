package com.loanapproval.dss.loan;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class LoanAppointmentRepository {

    private static final RowMapper<LoanAppointmentSummary> APPOINTMENT_ROW_MAPPER = (rs, rowNum) ->
            new LoanAppointmentSummary(
                    rs.getLong("id"),
                    toInstant(rs.getTimestamp("scheduled_at")),
                    rs.getString("location"),
                    rs.getString("note"),
                    rs.getString("status"),
                    toInstant(rs.getTimestamp("created_at")));

    private final JdbcTemplate jdbcTemplate;

    public LoanAppointmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<LoanAppointmentSummary> findLatestByLoanRequestId(Long loanRequestId) {
        return jdbcTemplate.query(
                """
                        SELECT id, scheduled_at, location, note, status, created_at
                        FROM loan_appointments
                        WHERE loan_request_id = ?
                        ORDER BY created_at DESC, id DESC
                        LIMIT 1
                        """,
                APPOINTMENT_ROW_MAPPER,
                loanRequestId).stream().findFirst();
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }
}

