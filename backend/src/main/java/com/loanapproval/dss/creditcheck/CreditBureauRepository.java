package com.loanapproval.dss.creditcheck;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CreditBureauRepository {

    private static final RowMapper<CreditBureauRecord> CREDIT_BUREAU_ROW_MAPPER = (rs, rowNum) -> new CreditBureauRecord(
        rs.getString("identity_number"),
        rs.getString("borrower_name"),
        CreditBureauStatus.valueOf(rs.getString("bureau_status")),
        (Integer) rs.getObject("credit_score"),
        (Integer) rs.getObject("active_loan_count"),
        (Integer) rs.getObject("days_past_due"),
        rs.getBoolean("manual_review_required"),
        rs.getBoolean("hard_reject"),
        rs.getString("risk_note"),
        rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toInstant() : null
    );

    private final JdbcTemplate jdbcTemplate;

    public CreditBureauRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<CreditBureauRecord> findByIdentityNumber(String identityNumber) {
        return jdbcTemplate.query(
            """
            SELECT
                identity_number,
                borrower_name,
                bureau_status,
                credit_score,
                active_loan_count,
                days_past_due,
                manual_review_required,
                hard_reject,
                risk_note,
                updated_at
            FROM credit_bureau_records
            WHERE identity_number = ?
            """,
            CREDIT_BUREAU_ROW_MAPPER,
            identityNumber
        ).stream().findFirst();
    }

    public long count(CreditBureauStatus status, String query) {
        QuerySpec querySpec = buildQuerySpec(status, query);
        Long count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM credit_bureau_records
            """ + querySpec.whereClause(),
            Long.class,
            querySpec.params().toArray()
        );
        return count != null ? count : 0L;
    }

    public List<CreditBureauRecord> findPaged(CreditBureauStatus status, String query, int offset, int limit) {
        QuerySpec querySpec = buildQuerySpec(status, query);
        List<Object> params = new ArrayList<>(querySpec.params());
        params.add(limit);
        params.add(offset);
        return jdbcTemplate.query(
            """
            SELECT
                identity_number,
                borrower_name,
                bureau_status,
                credit_score,
                active_loan_count,
                days_past_due,
                manual_review_required,
                hard_reject,
                risk_note,
                updated_at
            FROM credit_bureau_records
            """ + querySpec.whereClause() + """
            ORDER BY updated_at DESC, identity_number ASC
            LIMIT ? OFFSET ?
            """,
            CREDIT_BUREAU_ROW_MAPPER,
            params.toArray()
        );
    }

    public CreditBureauRecord upsert(CreditBureauRecord record) {
        jdbcTemplate.update(
            """
            INSERT INTO credit_bureau_records (
                identity_number,
                borrower_name,
                bureau_status,
                credit_score,
                active_loan_count,
                days_past_due,
                manual_review_required,
                hard_reject,
                risk_note
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                borrower_name = VALUES(borrower_name),
                bureau_status = VALUES(bureau_status),
                credit_score = VALUES(credit_score),
                active_loan_count = VALUES(active_loan_count),
                days_past_due = VALUES(days_past_due),
                manual_review_required = VALUES(manual_review_required),
                hard_reject = VALUES(hard_reject),
                risk_note = VALUES(risk_note),
                updated_at = CURRENT_TIMESTAMP
            """,
            record.identityNumber(),
            record.borrowerName(),
            record.bureauStatus().name(),
            record.creditScore(),
            valueOrZero(record.activeLoanCount()),
            valueOrZero(record.daysPastDue()),
            record.manualReviewRequired(),
            record.hardReject(),
            record.riskNote()
        );
        return findByIdentityNumber(record.identityNumber()).orElse(record);
    }

    public int deleteByIdentityNumber(String identityNumber) {
        return jdbcTemplate.update(
            "DELETE FROM credit_bureau_records WHERE identity_number = ?",
            identityNumber
        );
    }

    private QuerySpec buildQuerySpec(CreditBureauStatus status, String query) {
        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        if (status != null) {
            conditions.add("bureau_status = ?");
            params.add(status.name());
        }
        if (query != null && !query.isBlank()) {
            conditions.add("(identity_number LIKE ? OR borrower_name LIKE ? OR risk_note LIKE ?)");
            String likeValue = "%" + query.trim() + "%";
            params.add(likeValue);
            params.add(likeValue);
            params.add(likeValue);
        }

        String whereClause = conditions.isEmpty()
            ? ""
            : " WHERE " + String.join(" AND ", conditions);
        return new QuerySpec(whereClause, params);
    }

    private int valueOrZero(Integer value) {
        return value != null ? value : 0;
    }

    private record QuerySpec(String whereClause, List<Object> params) {
    }
}
