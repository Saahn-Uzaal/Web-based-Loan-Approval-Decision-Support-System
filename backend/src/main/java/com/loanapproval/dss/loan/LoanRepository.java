package com.loanapproval.dss.loan;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

@Repository
public class LoanRepository {

    private final JdbcTemplate jdbcTemplate;
    private final SimpleJdbcInsert insertLoan;

    public LoanRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.insertLoan = new SimpleJdbcInsert(jdbcTemplate)
            .withTableName("loan_requests")
            .usingColumns("customer_id", "amount", "term_months", "purpose", "status", "final_reason")
            .usingGeneratedKeyColumns("id");
    }

    public LoanRecord create(Long customerId, BigDecimal amount, Integer termMonths, LoanPurpose purpose) {
        Map<String, Object> values = new HashMap<>();
        values.put("customer_id", customerId);
        values.put("amount", amount);
        values.put("term_months", termMonths);
        values.put("purpose", purpose.name());
        values.put("status", LoanStatus.PENDING.name());
        values.put("final_reason", null);

        Number id = insertLoan.executeAndReturnKey(
            values
        );
        return findOwnedById(id.longValue(), customerId)
            .orElseThrow(() -> new IllegalStateException("Created loan request was not found"));
    }

    public List<LoanRecord> findByCustomerId(Long customerId) {
        return jdbcTemplate.query(
            """
            SELECT id, customer_id, amount, term_months, purpose, status, final_reason, created_at, updated_at
            FROM loan_requests
            WHERE customer_id = ?
            ORDER BY created_at DESC, id DESC
            """,
            (rs, rowNum) -> new LoanRecord(
                rs.getLong("id"),
                rs.getLong("customer_id"),
                rs.getBigDecimal("amount"),
                rs.getInt("term_months"),
                LoanPurpose.valueOf(rs.getString("purpose")),
                LoanStatus.valueOf(rs.getString("status")),
                rs.getString("final_reason"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at"))
            ),
            customerId
        );
    }

    public long countByCustomerId(Long customerId) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM loan_requests WHERE customer_id = ?",
            Long.class,
            customerId
        );
        return count != null ? count : 0L;
    }

    public List<LoanRecord> findByCustomerIdPaged(Long customerId, int offset, int limit) {
        return jdbcTemplate.query(
            """
            SELECT id, customer_id, amount, term_months, purpose, status, final_reason, created_at, updated_at
            FROM loan_requests
            WHERE customer_id = ?
            ORDER BY created_at DESC, id DESC
            LIMIT ? OFFSET ?
            """,
            (rs, rowNum) -> new LoanRecord(
                rs.getLong("id"),
                rs.getLong("customer_id"),
                rs.getBigDecimal("amount"),
                rs.getInt("term_months"),
                LoanPurpose.valueOf(rs.getString("purpose")),
                LoanStatus.valueOf(rs.getString("status")),
                rs.getString("final_reason"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at"))
            ),
            customerId, limit, offset
        );
    }

    public Optional<LoanRecord> findOwnedById(Long id, Long customerId) {
        return jdbcTemplate.query(
            """
            SELECT id, customer_id, amount, term_months, purpose, status, final_reason, created_at, updated_at
            FROM loan_requests
            WHERE id = ? AND customer_id = ?
            """,
            (rs, rowNum) -> new LoanRecord(
                rs.getLong("id"),
                rs.getLong("customer_id"),
                rs.getBigDecimal("amount"),
                rs.getInt("term_months"),
                LoanPurpose.valueOf(rs.getString("purpose")),
                LoanStatus.valueOf(rs.getString("status")),
                rs.getString("final_reason"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at"))
            ),
            id,
            customerId
        ).stream().findFirst();
    }

    public Optional<LoanRecord> findById(Long id) {
        return jdbcTemplate.query(
            """
            SELECT id, customer_id, amount, term_months, purpose, status, final_reason, created_at, updated_at
            FROM loan_requests
            WHERE id = ?
            """,
            (rs, rowNum) -> new LoanRecord(
                rs.getLong("id"),
                rs.getLong("customer_id"),
                rs.getBigDecimal("amount"),
                rs.getInt("term_months"),
                LoanPurpose.valueOf(rs.getString("purpose")),
                LoanStatus.valueOf(rs.getString("status")),
                rs.getString("final_reason"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at"))
            ),
            id
        ).stream().findFirst();
    }

    public void updateStatus(Long id, LoanStatus status) {
        jdbcTemplate.update(
            """
            UPDATE loan_requests
            SET status = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """,
            status.name(),
            id
        );
    }

    public int updateStatusAndReason(Long id, LoanStatus status, String reason) {
        return jdbcTemplate.update(
            """
            UPDATE loan_requests
            SET status = ?, final_reason = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """,
            status.name(),
            reason,
            id
        );
    }

    private java.time.Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }
}
