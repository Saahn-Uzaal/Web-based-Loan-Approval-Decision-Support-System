package com.loanapproval.dss.staff;

import com.loanapproval.dss.dss.CustomerSegment;
import com.loanapproval.dss.dss.DssRecommendation;
import com.loanapproval.dss.dss.RiskRank;
import com.loanapproval.dss.loan.LoanPurpose;
import com.loanapproval.dss.loan.LoanStatus;
import com.loanapproval.dss.staff.dto.StaffRequestDetailResponse;
import com.loanapproval.dss.staff.dto.StaffRequestSummaryResponse;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StaffReviewRepository {

    private final JdbcTemplate jdbcTemplate;

    public StaffReviewRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<StaffRequestSummaryResponse> findReviewQueue(LoanStatus status) {
        if (status == null) {
            return jdbcTemplate.query(
                """
                SELECT
                    lr.id,
                    lr.customer_id,
                    u.email AS customer_email,
                    cp.full_name AS customer_name,
                    lr.amount,
                    lr.term_months,
                    lr.purpose,
                    lr.status,
                    dr.risk_rank,
                    dr.recommendation,
                    lr.created_at
                FROM loan_requests lr
                INNER JOIN users u ON u.id = lr.customer_id
                LEFT JOIN customer_profiles cp ON cp.user_id = lr.customer_id
                LEFT JOIN dss_results dr ON dr.loan_request_id = lr.id
                WHERE lr.status IN ('PENDING', 'WAITING_SUPERVISOR')
                ORDER BY lr.created_at DESC, lr.id DESC
                """,
                (rs, rowNum) -> new StaffRequestSummaryResponse(
                    rs.getLong("id"),
                    rs.getLong("customer_id"),
                    rs.getString("customer_email"),
                    rs.getString("customer_name"),
                    rs.getBigDecimal("amount"),
                    rs.getInt("term_months"),
                    LoanPurpose.valueOf(rs.getString("purpose")),
                    LoanStatus.valueOf(rs.getString("status")),
                    parseEnum(RiskRank.class, rs.getString("risk_rank")),
                    parseEnum(DssRecommendation.class, rs.getString("recommendation")),
                    toInstant(rs.getTimestamp("created_at"))
                )
            );
        }

        return jdbcTemplate.query(
            """
            SELECT
                lr.id,
                lr.customer_id,
                u.email AS customer_email,
                cp.full_name AS customer_name,
                lr.amount,
                lr.term_months,
                lr.purpose,
                lr.status,
                dr.risk_rank,
                dr.recommendation,
                lr.created_at
            FROM loan_requests lr
            INNER JOIN users u ON u.id = lr.customer_id
            LEFT JOIN customer_profiles cp ON cp.user_id = lr.customer_id
            LEFT JOIN dss_results dr ON dr.loan_request_id = lr.id
            WHERE lr.status = ?
            ORDER BY lr.created_at DESC, lr.id DESC
            """,
            (rs, rowNum) -> new StaffRequestSummaryResponse(
                rs.getLong("id"),
                rs.getLong("customer_id"),
                rs.getString("customer_email"),
                rs.getString("customer_name"),
                rs.getBigDecimal("amount"),
                rs.getInt("term_months"),
                LoanPurpose.valueOf(rs.getString("purpose")),
                LoanStatus.valueOf(rs.getString("status")),
                parseEnum(RiskRank.class, rs.getString("risk_rank")),
                parseEnum(DssRecommendation.class, rs.getString("recommendation")),
                toInstant(rs.getTimestamp("created_at"))
            ),
            status.name()
        );
    }

    public long countReviewQueue(LoanStatus status) {
        if (status == null) {
            Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM loan_requests WHERE status IN ('PENDING', 'WAITING_SUPERVISOR')",
                Long.class
            );
            return count != null ? count : 0L;
        }
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM loan_requests WHERE status = ?",
            Long.class,
            status.name()
        );
        return count != null ? count : 0L;
    }

    public List<StaffRequestSummaryResponse> findReviewQueuePaged(LoanStatus status, int offset, int limit) {
        if (status == null) {
            return jdbcTemplate.query(
                """
                SELECT
                    lr.id,
                    lr.customer_id,
                    u.email AS customer_email,
                    cp.full_name AS customer_name,
                    lr.amount,
                    lr.term_months,
                    lr.purpose,
                    lr.status,
                    dr.risk_rank,
                    dr.recommendation,
                    lr.created_at
                FROM loan_requests lr
                INNER JOIN users u ON u.id = lr.customer_id
                LEFT JOIN customer_profiles cp ON cp.user_id = lr.customer_id
                LEFT JOIN dss_results dr ON dr.loan_request_id = lr.id
                WHERE lr.status IN ('PENDING', 'WAITING_SUPERVISOR')
                ORDER BY lr.created_at DESC, lr.id DESC
                LIMIT ? OFFSET ?
                """,
                (rs, rowNum) -> new StaffRequestSummaryResponse(
                    rs.getLong("id"),
                    rs.getLong("customer_id"),
                    rs.getString("customer_email"),
                    rs.getString("customer_name"),
                    rs.getBigDecimal("amount"),
                    rs.getInt("term_months"),
                    LoanPurpose.valueOf(rs.getString("purpose")),
                    LoanStatus.valueOf(rs.getString("status")),
                    parseEnum(RiskRank.class, rs.getString("risk_rank")),
                    parseEnum(DssRecommendation.class, rs.getString("recommendation")),
                    toInstant(rs.getTimestamp("created_at"))
                ),
                limit, offset
            );
        }

        return jdbcTemplate.query(
            """
            SELECT
                lr.id,
                lr.customer_id,
                u.email AS customer_email,
                cp.full_name AS customer_name,
                lr.amount,
                lr.term_months,
                lr.purpose,
                lr.status,
                dr.risk_rank,
                dr.recommendation,
                lr.created_at
            FROM loan_requests lr
            INNER JOIN users u ON u.id = lr.customer_id
            LEFT JOIN customer_profiles cp ON cp.user_id = lr.customer_id
            LEFT JOIN dss_results dr ON dr.loan_request_id = lr.id
            WHERE lr.status = ?
            ORDER BY lr.created_at DESC, lr.id DESC
            LIMIT ? OFFSET ?
            """,
            (rs, rowNum) -> new StaffRequestSummaryResponse(
                rs.getLong("id"),
                rs.getLong("customer_id"),
                rs.getString("customer_email"),
                rs.getString("customer_name"),
                rs.getBigDecimal("amount"),
                rs.getInt("term_months"),
                LoanPurpose.valueOf(rs.getString("purpose")),
                LoanStatus.valueOf(rs.getString("status")),
                parseEnum(RiskRank.class, rs.getString("risk_rank")),
                parseEnum(DssRecommendation.class, rs.getString("recommendation")),
                toInstant(rs.getTimestamp("created_at"))
            ),
            status.name(), limit, offset
        );
    }

    public Optional<LoanStatus> findStatusByLoanRequestId(Long loanRequestId) {
        return jdbcTemplate.query(
            """
            SELECT status
            FROM loan_requests
            WHERE id = ?
            """,
            (rs, rowNum) -> LoanStatus.valueOf(rs.getString("status")),
            loanRequestId
        ).stream().findFirst();
    }

    public Optional<StaffRequestDetailResponse> findRequestDetailById(Long loanRequestId) {
        return jdbcTemplate.query(
            """
            SELECT
                lr.id,
                lr.status,
                lr.amount,
                lr.term_months,
                lr.purpose,
                lr.final_reason,
                lr.created_at,
                lr.updated_at,
                u.id AS customer_id,
                u.email AS customer_email,
                cp.full_name,
                cp.phone,
                cp.monthly_income,
                cp.debt_to_income_ratio,
                cp.employment_status,
                cv.document_status,
                cv.identity_status,
                cv.income_status,
                cv.kyc_status,
                cv.aml_status,
                cv.fraud_flag,
                cv.note AS verification_note,
                cv.verified_at,
                dr.credit_score,
                dr.risk_rank,
                dr.customer_segment,
                dr.recommendation,
                dr.explanation,
                dr.created_at AS dss_created_at,
                ra.credit_risk_score,
                ra.fraud_risk_score,
                ra.operational_risk_score,
                ra.overall_risk_level,
                ra.risk_reasons,
                ra.created_at AS risk_created_at,
                lc.id AS contract_id,
                lc.status AS contract_status,
                lc.annual_interest_rate,
                lc.monthly_payment,
                lc.total_interest,
                lc.created_at AS contract_created_at
            FROM loan_requests lr
            INNER JOIN users u ON u.id = lr.customer_id
            LEFT JOIN customer_profiles cp ON cp.user_id = lr.customer_id
            LEFT JOIN customer_verifications cv ON cv.customer_id = lr.customer_id
            LEFT JOIN dss_results dr ON dr.loan_request_id = lr.id
            LEFT JOIN risk_assessments ra ON ra.loan_request_id = lr.id
            LEFT JOIN loan_contracts lc ON lc.loan_request_id = lr.id
            WHERE lr.id = ?
            """,
            (rs, rowNum) -> {
                StaffRequestDetailResponse.CustomerSummary customerSummary =
                    new StaffRequestDetailResponse.CustomerSummary(
                        rs.getLong("customer_id"),
                        rs.getString("customer_email")
                    );

                StaffRequestDetailResponse.CustomerProfileSummary customerProfileSummary =
                    new StaffRequestDetailResponse.CustomerProfileSummary(
                        rs.getString("full_name"),
                        rs.getString("phone"),
                        rs.getBigDecimal("monthly_income"),
                        rs.getBigDecimal("debt_to_income_ratio"),
                        rs.getString("employment_status")
                    );

                StaffRequestDetailResponse.DssSummary dssSummary = null;
                if (rs.getObject("credit_score") != null) {
                    dssSummary = new StaffRequestDetailResponse.DssSummary(
                        rs.getInt("credit_score"),
                        parseEnum(RiskRank.class, rs.getString("risk_rank")),
                        parseEnum(CustomerSegment.class, rs.getString("customer_segment")),
                        parseEnum(DssRecommendation.class, rs.getString("recommendation")),
                        rs.getString("explanation"),
                        toInstant(rs.getTimestamp("dss_created_at"))
                    );
                }

                StaffRequestDetailResponse.VerificationSummary verificationSummary = null;
                if (rs.getString("document_status") != null) {
                    verificationSummary = new StaffRequestDetailResponse.VerificationSummary(
                        rs.getString("document_status"),
                        rs.getString("identity_status"),
                        rs.getString("income_status"),
                        rs.getString("kyc_status"),
                        rs.getString("aml_status"),
                        rs.getBoolean("fraud_flag"),
                        rs.getString("verification_note"),
                        toInstant(rs.getTimestamp("verified_at"))
                    );
                }

                StaffRequestDetailResponse.RiskAssessmentSummary riskSummary = null;
                if (rs.getObject("credit_risk_score") != null) {
                    riskSummary = new StaffRequestDetailResponse.RiskAssessmentSummary(
                        rs.getInt("credit_risk_score"),
                        rs.getInt("fraud_risk_score"),
                        rs.getInt("operational_risk_score"),
                        rs.getString("overall_risk_level"),
                        rs.getString("risk_reasons"),
                        toInstant(rs.getTimestamp("risk_created_at"))
                    );
                }

                StaffRequestDetailResponse.LoanContractSummary contractSummary = null;
                if (rs.getObject("contract_id") != null) {
                    contractSummary = new StaffRequestDetailResponse.LoanContractSummary(
                        rs.getLong("contract_id"),
                        rs.getString("contract_status"),
                        rs.getBigDecimal("annual_interest_rate"),
                        rs.getBigDecimal("monthly_payment"),
                        rs.getBigDecimal("total_interest"),
                        toInstant(rs.getTimestamp("contract_created_at"))
                    );
                }

                return new StaffRequestDetailResponse(
                    rs.getLong("id"),
                    LoanStatus.valueOf(rs.getString("status")),
                    rs.getBigDecimal("amount"),
                    rs.getInt("term_months"),
                    LoanPurpose.valueOf(rs.getString("purpose")),
                    rs.getString("final_reason"),
                    toInstant(rs.getTimestamp("created_at")),
                    toInstant(rs.getTimestamp("updated_at")),
                    customerSummary,
                    customerProfileSummary,
                    dssSummary,
                    verificationSummary,
                    riskSummary,
                    contractSummary,
                    List.of()
                );
            },
            loanRequestId
        ).stream().findFirst();
    }

    public List<StaffRequestDetailResponse.DecisionAuditEntry> findDecisionAuditsByLoanRequestId(Long loanRequestId) {
        return jdbcTemplate.query(
            """
            SELECT
                da.id,
                da.staff_user_id,
                u.email AS staff_email,
                da.action,
                da.reason,
                da.created_at
            FROM decision_audits da
            INNER JOIN users u ON u.id = da.staff_user_id
            WHERE da.loan_request_id = ?
            ORDER BY da.created_at DESC, da.id DESC
            """,
            (rs, rowNum) -> new StaffRequestDetailResponse.DecisionAuditEntry(
                rs.getLong("id"),
                rs.getLong("staff_user_id"),
                rs.getString("staff_email"),
                StaffDecisionAction.valueOf(rs.getString("action")),
                rs.getString("reason"),
                toInstant(rs.getTimestamp("created_at"))
            ),
            loanRequestId
        );
    }

    public int updateFinalDecision(Long loanRequestId, LoanStatus status, String reason) {
        return jdbcTemplate.update(
            """
            UPDATE loan_requests
            SET status = ?, final_reason = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """,
            status.name(),
            reason,
            loanRequestId
        );
    }

    public void insertDecisionAudit(
        Long loanRequestId,
        Long staffUserId,
        StaffDecisionAction action,
        String reason
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO decision_audits (loan_request_id, staff_user_id, action, reason)
            VALUES (?, ?, ?, ?)
            """,
            loanRequestId,
            staffUserId,
            action.name(),
            reason
        );
    }

    private java.time.Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }

    private <T extends Enum<T>> T parseEnum(Class<T> enumType, String value) {
        return value != null ? Enum.valueOf(enumType, value) : null;
    }
}
