package com.loanapproval.dss.risk;

import java.sql.Timestamp;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RiskAssessmentRepository {

    private final JdbcTemplate jdbcTemplate;

    public RiskAssessmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsert(RiskAssessment riskAssessment) {
        jdbcTemplate.update(
            """
            INSERT INTO risk_assessments (
                loan_request_id,
                credit_risk_score,
                fraud_risk_score,
                operational_risk_score,
                overall_risk_level,
                risk_reasons
            ) VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                credit_risk_score = VALUES(credit_risk_score),
                fraud_risk_score = VALUES(fraud_risk_score),
                operational_risk_score = VALUES(operational_risk_score),
                overall_risk_level = VALUES(overall_risk_level),
                risk_reasons = VALUES(risk_reasons)
            """,
            riskAssessment.loanRequestId(),
            riskAssessment.creditRiskScore(),
            riskAssessment.fraudRiskScore(),
            riskAssessment.operationalRiskScore(),
            riskAssessment.overallRiskLevel().name(),
            riskAssessment.riskReasons()
        );
    }

    public Optional<RiskAssessment> findByLoanRequestId(Long loanRequestId) {
        return jdbcTemplate.query(
            """
            SELECT
                loan_request_id,
                credit_risk_score,
                fraud_risk_score,
                operational_risk_score,
                overall_risk_level,
                risk_reasons,
                created_at
            FROM risk_assessments
            WHERE loan_request_id = ?
            """,
            (rs, rowNum) -> new RiskAssessment(
                rs.getLong("loan_request_id"),
                rs.getInt("credit_risk_score"),
                rs.getInt("fraud_risk_score"),
                rs.getInt("operational_risk_score"),
                RiskLevel.valueOf(rs.getString("overall_risk_level")),
                rs.getString("risk_reasons"),
                toInstant(rs.getTimestamp("created_at"))
            ),
            loanRequestId
        ).stream().findFirst();
    }

    private java.time.Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }
}
