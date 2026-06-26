package com.loanapproval.dss.creditcheck;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerCreditCheckRepository {

    private final JdbcTemplate jdbcTemplate;

    public CustomerCreditCheckRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public CustomerCreditCheckRecord create(CustomerCreditCheckRecord record) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO customer_credit_checks (
                    customer_id,
                    identity_number,
                    matched_record,
                    bureau_status,
                    credit_score,
                    active_loan_count,
                    days_past_due,
                    total_monthly_obligation,
                    total_outstanding_balance,
                    external_monthly_obligation,
                    external_outstanding_balance,
                    reporting_institution_count,
                    manual_review_required,
                    hard_reject,
                    risk_note,
                    source,
                    checked_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, record.customerId());
            statement.setString(2, record.identityNumber());
            statement.setBoolean(3, record.matchedRecord());
            statement.setString(4, record.bureauStatus().name());
            statement.setObject(5, record.creditScore());
            statement.setInt(6, valueOrZero(record.activeLoanCount()));
            statement.setInt(7, valueOrZero(record.daysPastDue()));
            statement.setBigDecimal(8, valueOrZero(record.totalMonthlyObligation()));
            statement.setBigDecimal(9, valueOrZero(record.totalOutstandingBalance()));
            statement.setBigDecimal(10, valueOrZero(record.externalMonthlyObligation()));
            statement.setBigDecimal(11, valueOrZero(record.externalOutstandingBalance()));
            statement.setInt(12, valueOrZero(record.reportingInstitutionCount()));
            statement.setBoolean(13, record.manualReviewRequired());
            statement.setBoolean(14, record.hardReject());
            statement.setString(15, record.riskNote());
            statement.setString(16, record.source());
            statement.setTimestamp(17, record.checkedAt() != null ? Timestamp.from(record.checkedAt()) : Timestamp.from(java.time.Instant.now()));
            return statement;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            return record;
        }
        return new CustomerCreditCheckRecord(
            key.longValue(),
            record.customerId(),
            record.identityNumber(),
            record.matchedRecord(),
            record.bureauStatus(),
            record.creditScore(),
            record.activeLoanCount(),
            record.daysPastDue(),
            record.totalMonthlyObligation(),
            record.totalOutstandingBalance(),
            record.externalMonthlyObligation(),
            record.externalOutstandingBalance(),
            record.reportingInstitutionCount(),
            record.manualReviewRequired(),
            record.hardReject(),
            record.riskNote(),
            record.source(),
            record.checkedAt()
        );
    }

    public Optional<CustomerCreditCheckRecord> findLatestByCustomerId(Long customerId) {
        return jdbcTemplate.query(
            """
            SELECT
                id,
                customer_id,
                identity_number,
                matched_record,
                bureau_status,
                credit_score,
                active_loan_count,
                days_past_due,
                total_monthly_obligation,
                total_outstanding_balance,
                external_monthly_obligation,
                external_outstanding_balance,
                reporting_institution_count,
                manual_review_required,
                hard_reject,
                risk_note,
                source,
                checked_at
            FROM customer_credit_checks
            WHERE customer_id = ?
            ORDER BY checked_at DESC, id DESC
            LIMIT 1
            """,
            (rs, rowNum) -> new CustomerCreditCheckRecord(
                rs.getLong("id"),
                rs.getLong("customer_id"),
                rs.getString("identity_number"),
                rs.getBoolean("matched_record"),
                CreditBureauStatus.valueOf(rs.getString("bureau_status")),
                (Integer) rs.getObject("credit_score"),
                (Integer) rs.getObject("active_loan_count"),
                (Integer) rs.getObject("days_past_due"),
                rs.getBigDecimal("total_monthly_obligation"),
                rs.getBigDecimal("total_outstanding_balance"),
                rs.getBigDecimal("external_monthly_obligation"),
                rs.getBigDecimal("external_outstanding_balance"),
                (Integer) rs.getObject("reporting_institution_count"),
                rs.getBoolean("manual_review_required"),
                rs.getBoolean("hard_reject"),
                rs.getString("risk_note"),
                rs.getString("source"),
                rs.getTimestamp("checked_at") != null ? rs.getTimestamp("checked_at").toInstant() : null
            ),
            customerId
        ).stream().findFirst();
    }

    private int valueOrZero(Integer value) {
        return value != null ? value : 0;
    }

    private java.math.BigDecimal valueOrZero(java.math.BigDecimal value) {
        return value != null ? value : java.math.BigDecimal.ZERO;
    }
}
