package com.loanapproval.dss.repayment;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

@Repository
public class RepaymentRepository {

    private final JdbcTemplate jdbcTemplate;
    private final SimpleJdbcInsert insertRepayment;

    public RepaymentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.insertRepayment = new SimpleJdbcInsert(jdbcTemplate)
            .withTableName("loan_repayments")
            .usingColumns(
                "loan_request_id",
                "customer_id",
                "amount_due",
                "amount_paid",
                "due_date",
                "paid_at",
                "payment_status",
                "rating_delta",
                "note"
            )
            .usingGeneratedKeyColumns("id");
    }

    public RepaymentRecord create(
        Long loanRequestId,
        Long customerId,
        BigDecimal amountDue,
        BigDecimal amountPaid,
        LocalDate dueDate,
        Instant paidAt,
        RepaymentStatus repaymentStatus,
        int ratingDelta,
        String note
    ) {
        Map<String, Object> values = new HashMap<>();
        values.put("loan_request_id", loanRequestId);
        values.put("customer_id", customerId);
        values.put("amount_due", amountDue);
        values.put("amount_paid", amountPaid);
        values.put("due_date", java.sql.Date.valueOf(dueDate));
        values.put("paid_at", Timestamp.from(paidAt));
        values.put("payment_status", repaymentStatus.name());
        values.put("rating_delta", ratingDelta);
        values.put("note", note);

        Number id = insertRepayment.executeAndReturnKey(values);
        return findByIdAndCustomerId(id.longValue(), customerId)
            .orElseThrow(() -> new IllegalStateException("Created repayment was not found"));
    }

    public List<RepaymentRecord> findByCustomerId(Long customerId) {
        return jdbcTemplate.query(
            """
            SELECT
                id,
                loan_request_id,
                customer_id,
                amount_due,
                amount_paid,
                due_date,
                paid_at,
                payment_status,
                rating_delta,
                note,
                created_at
            FROM loan_repayments
            WHERE customer_id = ?
            ORDER BY created_at DESC, id DESC
            """,
            (rs, rowNum) -> new RepaymentRecord(
                rs.getLong("id"),
                rs.getLong("loan_request_id"),
                rs.getLong("customer_id"),
                rs.getBigDecimal("amount_due"),
                rs.getBigDecimal("amount_paid"),
                rs.getDate("due_date").toLocalDate(),
                toInstant(rs.getTimestamp("paid_at")),
                RepaymentStatus.valueOf(rs.getString("payment_status")),
                rs.getInt("rating_delta"),
                rs.getString("note"),
                toInstant(rs.getTimestamp("created_at"))
            ),
            customerId
        );
    }

    public Optional<RepaymentRecord> findByIdAndCustomerId(Long id, Long customerId) {
        return jdbcTemplate.query(
            """
            SELECT
                id,
                loan_request_id,
                customer_id,
                amount_due,
                amount_paid,
                due_date,
                paid_at,
                payment_status,
                rating_delta,
                note,
                created_at
            FROM loan_repayments
            WHERE id = ? AND customer_id = ?
            """,
            (rs, rowNum) -> new RepaymentRecord(
                rs.getLong("id"),
                rs.getLong("loan_request_id"),
                rs.getLong("customer_id"),
                rs.getBigDecimal("amount_due"),
                rs.getBigDecimal("amount_paid"),
                rs.getDate("due_date").toLocalDate(),
                toInstant(rs.getTimestamp("paid_at")),
                RepaymentStatus.valueOf(rs.getString("payment_status")),
                rs.getInt("rating_delta"),
                rs.getString("note"),
                toInstant(rs.getTimestamp("created_at"))
            ),
            id,
            customerId
        ).stream().findFirst();
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }
}
