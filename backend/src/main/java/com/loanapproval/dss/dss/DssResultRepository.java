package com.loanapproval.dss.dss;

import java.sql.Timestamp;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DssResultRepository {

    private final JdbcTemplate jdbcTemplate;

    public DssResultRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsert(Long loanRequestId, DssResult dssResult) {
        jdbcTemplate.update(
            """
            INSERT INTO dss_results (
                loan_request_id, credit_score, risk_rank, customer_segment, recommendation, explanation
            ) VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                credit_score = VALUES(credit_score),
                risk_rank = VALUES(risk_rank),
                customer_segment = VALUES(customer_segment),
                recommendation = VALUES(recommendation),
                explanation = VALUES(explanation)
            """,
            loanRequestId,
            dssResult.creditScore(),
            dssResult.riskRank().name(),
            dssResult.customerSegment().name(),
            dssResult.recommendation().name(),
            dssResult.explanation()
        );
    }

    public Optional<DssResultRecord> findByLoanRequestId(Long loanRequestId) {
        return jdbcTemplate.query(
            """
            SELECT loan_request_id, credit_score, risk_rank, customer_segment, recommendation, explanation, created_at
            FROM dss_results
            WHERE loan_request_id = ?
            """,
            (rs, rowNum) -> new DssResultRecord(
                rs.getLong("loan_request_id"),
                rs.getInt("credit_score"),
                RiskRank.valueOf(rs.getString("risk_rank")),
                CustomerSegment.valueOf(rs.getString("customer_segment")),
                DssRecommendation.valueOf(rs.getString("recommendation")),
                rs.getString("explanation"),
                toInstant(rs.getTimestamp("created_at"))
            ),
            loanRequestId
        ).stream().findFirst();
    }

    private java.time.Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }
}
