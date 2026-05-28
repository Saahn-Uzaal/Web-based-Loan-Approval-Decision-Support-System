package com.loanapproval.dss.staff;

import com.loanapproval.dss.dss.CustomerSegment;
import com.loanapproval.dss.dss.DssRecommendation;
import com.loanapproval.dss.dss.RiskRank;
import com.loanapproval.dss.loan.CollateralType;
import com.loanapproval.dss.loan.LoanPurpose;
import com.loanapproval.dss.loan.LoanStatus;
import com.loanapproval.dss.loan.LoanType;
import com.loanapproval.dss.staff.dto.StaffRequestDetailResponse;
import com.loanapproval.dss.staff.dto.StaffRequestSummaryResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StaffReviewRepository {

    private static final String REVIEW_QUEUE_STATUSES_SQL = "'PENDING', 'NEEDS_MORE_INFO'";
    private static final String OPERATION_QUEUE_STATUSES_SQL =
            "'APPROVED', 'CONTRACTED', 'ACTIVE', 'OVERDUE'";

    private final JdbcTemplate jdbcTemplate;

    public StaffReviewRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<StaffRequestSummaryResponse> findReviewQueue(LoanStatus status) {
        return findQueue(status, REVIEW_QUEUE_STATUSES_SQL);
    }

    public long countReviewQueue(LoanStatus status) {
        return countQueue(status, REVIEW_QUEUE_STATUSES_SQL);
    }

    public List<StaffRequestSummaryResponse> findReviewQueuePaged(LoanStatus status, int offset, int limit) {
        return findQueuePaged(status, REVIEW_QUEUE_STATUSES_SQL, offset, limit);
    }

    public List<StaffRequestSummaryResponse> findOperationQueue(LoanStatus status) {
        return findQueue(status, OPERATION_QUEUE_STATUSES_SQL);
    }

    public long countOperationQueue(LoanStatus status) {
        return countQueue(status, OPERATION_QUEUE_STATUSES_SQL);
    }

    public List<StaffRequestSummaryResponse> findOperationQueuePaged(LoanStatus status, int offset, int limit) {
        return findQueuePaged(status, OPERATION_QUEUE_STATUSES_SQL, offset, limit);
    }

    private List<StaffRequestSummaryResponse> findQueue(LoanStatus status, String defaultStatusesSql) {
        if (status == null) {
            return jdbcTemplate.query(summarySql("lr.status IN (" + defaultStatusesSql + ")", false), this::mapSummary);
        }
        return jdbcTemplate.query(summarySql("lr.status = ?", false), this::mapSummary, status.name());
    }

    private long countQueue(LoanStatus status, String defaultStatusesSql) {
        Long count;
        if (status == null) {
            count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM loan_requests WHERE status IN (" + defaultStatusesSql + ")",
                    Long.class);
        } else {
            count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM loan_requests WHERE status = ?",
                    Long.class,
                    status.name());
        }
        return count != null ? count : 0L;
    }

    private List<StaffRequestSummaryResponse> findQueuePaged(
            LoanStatus status,
            String defaultStatusesSql,
            int offset,
            int limit) {
        if (status == null) {
            return jdbcTemplate.query(
                    summarySql("lr.status IN (" + defaultStatusesSql + ")", true),
                    this::mapSummary,
                    limit,
                    offset);
        }
        return jdbcTemplate.query(
                summarySql("lr.status = ?", true),
                this::mapSummary,
                status.name(),
                limit,
                offset);
    }

    private String summarySql(String whereClause, boolean paged) {
        String pagingClause = paged ? "LIMIT ? OFFSET ?" : "";
        return """
                SELECT
                    lr.id,
                    lr.customer_id,
                    lr.loan_type,
                    u.email AS customer_email,
                    cp.full_name AS customer_name,
                    lr.assigned_staff_user_id,
                    assigned_staff.email AS assigned_staff_email,
                    lr.assigned_at,
                    lr.amount,
                    lr.term_months,
                    lr.purpose,
                    lr.status,
                    lr.approved_amount,
                    lr.approved_monthly_payment,
                    dr.risk_rank,
                    dr.recommendation,
                    lr.created_at
                FROM loan_requests lr
                INNER JOIN users u ON u.id = lr.customer_id
                LEFT JOIN customer_profiles cp ON cp.user_id = lr.customer_id
                LEFT JOIN users assigned_staff ON assigned_staff.id = lr.assigned_staff_user_id
                LEFT JOIN dss_results dr ON dr.loan_request_id = lr.id
                WHERE %s
                ORDER BY lr.created_at DESC, lr.id DESC
                %s
                """.formatted(whereClause, pagingClause);
    }

    private StaffRequestSummaryResponse mapSummary(ResultSet rs, int rowNum) throws SQLException {
        return new StaffRequestSummaryResponse(
                rs.getLong("id"),
                rs.getLong("customer_id"),
                LoanType.valueOf(rs.getString("loan_type")),
                rs.getString("customer_email"),
                rs.getString("customer_name"),
                (Long) rs.getObject("assigned_staff_user_id"),
                rs.getString("assigned_staff_email"),
                toInstant(rs.getTimestamp("assigned_at")),
                rs.getBigDecimal("amount"),
                rs.getInt("term_months"),
                LoanPurpose.valueOf(rs.getString("purpose")),
                LoanStatus.valueOf(rs.getString("status")),
                rs.getBigDecimal("approved_amount"),
                rs.getBigDecimal("approved_monthly_payment"),
                parseEnum(RiskRank.class, rs.getString("risk_rank")),
                parseEnum(DssRecommendation.class, rs.getString("recommendation")),
                toInstant(rs.getTimestamp("created_at")));
    }

    public Optional<LoanStatus> findStatusByLoanRequestId(Long loanRequestId) {
        return jdbcTemplate.query(
                """
                        SELECT status
                        FROM loan_requests
                        WHERE id = ?
                        """,
                (rs, rowNum) -> LoanStatus.valueOf(rs.getString("status")),
                loanRequestId).stream().findFirst();
    }

    public Optional<StaffRequestDetailResponse> findRequestDetailById(Long loanRequestId) {
        return jdbcTemplate.query(
                """
                        SELECT
                            lr.id,
                            lr.loan_type,
                            lr.status,
                            lr.amount,
                            lr.term_months,
                            lr.purpose,
                            lr.collateral_type,
                            lr.final_reason,
                            lr.eligible_limit,
                            lr.approved_amount,
                            lr.approved_term_months,
                            lr.approved_annual_rate,
                            lr.approved_monthly_payment,
                            lr.decision_policy_version,
                            lr.intake_note,
                            lr.created_at,
                            lr.updated_at,
                            u.id AS customer_id,
                            u.email AS customer_email,
                            lr.assigned_staff_user_id,
                            assigned_staff.email AS assigned_staff_email,
                            lr.assigned_at,
                            COALESCE(las.full_name, cp.full_name) AS full_name,
                            COALESCE(las.phone, cp.phone) AS phone,
                            cp.identity_number,
                            COALESCE(las.declared_monthly_income, cp.monthly_income) AS monthly_income,
                            COALESCE(las.verified_monthly_income, cp.verified_monthly_income) AS profile_verified_monthly_income,
                            COALESCE(las.debt_to_income_ratio, cp.debt_to_income_ratio) AS debt_to_income_ratio,
                            COALESCE(las.employment_status, cp.employment_status) AS employment_status,
                            COALESCE(las.employment_start_date, cp.employment_start_date) AS employment_start_date,
                            cp.bank_account_number,
                            cp.bank_name,
                            COALESCE(las.credit_history_score, cp.credit_history_score) AS credit_history_score,
                            cp.payslip_original_filename,
                            cp.payslip_file_size,
                            cp.payslip_uploaded_at,
                            cp.identity_card_front_original_filename,
                            cp.identity_card_front_file_size,
                            cp.identity_card_front_uploaded_at,
                            cp.identity_card_back_original_filename,
                            cp.identity_card_back_file_size,
                            cp.identity_card_back_uploaded_at,
                            CASE
                                WHEN las.loan_request_id IS NOT NULL THEN las.document_status
                                ELSE 'PENDING'
                            END AS document_status,
                            CASE
                                WHEN las.loan_request_id IS NOT NULL THEN las.identity_status
                                ELSE 'PENDING'
                            END AS identity_status,
                            CASE
                                WHEN las.loan_request_id IS NOT NULL THEN las.face_match_status
                                ELSE 'PENDING'
                            END AS face_match_status,
                            CASE
                                WHEN las.loan_request_id IS NOT NULL THEN las.income_status
                                ELSE 'PENDING'
                            END AS income_status,
                            las.verified_monthly_income,
                            CASE
                                WHEN las.loan_request_id IS NOT NULL THEN las.kyc_status
                                ELSE 'PENDING'
                            END AS kyc_status,
                            CASE
                                WHEN las.loan_request_id IS NOT NULL THEN las.aml_status
                                ELSE 'PENDING'
                            END AS aml_status,
                            CASE
                                WHEN las.loan_request_id IS NOT NULL THEN las.fraud_flag
                                ELSE NULL
                            END AS fraud_flag,
                            CASE
                                WHEN las.loan_request_id IS NOT NULL THEN las.verification_note
                                ELSE NULL
                            END AS verification_note,
                            CASE
                                WHEN las.loan_request_id IS NOT NULL THEN las.verified_at
                                ELSE NULL
                            END AS verified_at,
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
                            lc.created_at AS contract_created_at,
                            la.id AS appointment_id,
                            la.staff_id AS appointment_staff_id,
                            appointment_staff.email AS appointment_staff_email,
                            la.scheduled_at AS appointment_scheduled_at,
                            la.location AS appointment_location,
                            la.note AS appointment_note,
                            la.status AS appointment_status,
                            la.created_at AS appointment_created_at
                        FROM loan_requests lr
                        INNER JOIN users u ON u.id = lr.customer_id
                        LEFT JOIN users assigned_staff ON assigned_staff.id = lr.assigned_staff_user_id
                        LEFT JOIN customer_profiles cp ON cp.user_id = lr.customer_id
                        LEFT JOIN loan_application_snapshots las ON las.loan_request_id = lr.id
                        LEFT JOIN dss_results dr ON dr.loan_request_id = lr.id
                        LEFT JOIN risk_assessments ra ON ra.loan_request_id = lr.id
                        LEFT JOIN loan_contracts lc ON lc.loan_request_id = lr.id
                        LEFT JOIN loan_appointments la ON la.id = (
                            SELECT la_latest.id
                            FROM loan_appointments la_latest
                            WHERE la_latest.loan_request_id = lr.id
                            ORDER BY la_latest.created_at DESC, la_latest.id DESC
                            LIMIT 1
                        )
                        LEFT JOIN users appointment_staff ON appointment_staff.id = la.staff_id
                        WHERE lr.id = ?
                        """,
                (rs, rowNum) -> {
                    StaffRequestDetailResponse.CustomerSummary customerSummary = new StaffRequestDetailResponse.CustomerSummary(
                            rs.getLong("customer_id"),
                            rs.getString("customer_email"));

                    StaffRequestDetailResponse.AssignmentSummary assignmentSummary = null;
                    if (rs.getObject("assigned_staff_user_id") != null) {
                        assignmentSummary = new StaffRequestDetailResponse.AssignmentSummary(
                                rs.getLong("assigned_staff_user_id"),
                                rs.getString("assigned_staff_email"),
                                toInstant(rs.getTimestamp("assigned_at")));
                    }

                    StaffRequestDetailResponse.CustomerProfileSummary customerProfileSummary = new StaffRequestDetailResponse.CustomerProfileSummary(
                            rs.getString("full_name"),
                            rs.getString("phone"),
                            rs.getString("identity_number"),
                            rs.getBigDecimal("monthly_income"),
                            rs.getBigDecimal("profile_verified_monthly_income"),
                            rs.getBigDecimal("debt_to_income_ratio"),
                            rs.getString("employment_status"),
                            rs.getObject("employment_start_date", java.time.LocalDate.class),
                            rs.getString("bank_account_number"),
                            rs.getString("bank_name"),
                            (Integer) rs.getObject("credit_history_score"),
                            null,
                            rs.getString("payslip_original_filename"),
                            (Long) rs.getObject("payslip_file_size"),
                            toInstant(rs.getTimestamp("payslip_uploaded_at")),
                            rs.getString("identity_card_front_original_filename"),
                            (Long) rs.getObject("identity_card_front_file_size"),
                            toInstant(rs.getTimestamp("identity_card_front_uploaded_at")),
                            rs.getString("identity_card_back_original_filename"),
                            (Long) rs.getObject("identity_card_back_file_size"),
                            toInstant(rs.getTimestamp("identity_card_back_uploaded_at")));

                    StaffRequestDetailResponse.DssSummary dssSummary = null;
                    if (rs.getObject("credit_score") != null) {
                        dssSummary = new StaffRequestDetailResponse.DssSummary(
                                rs.getInt("credit_score"),
                                parseEnum(RiskRank.class, rs.getString("risk_rank")),
                                parseEnum(CustomerSegment.class, rs.getString("customer_segment")),
                                parseEnum(DssRecommendation.class, rs.getString("recommendation")),
                                rs.getString("explanation"),
                                toInstant(rs.getTimestamp("dss_created_at")));
                    }

                    StaffRequestDetailResponse.VerificationSummary verificationSummary = null;
                    if (rs.getString("document_status") != null) {
                        verificationSummary = new StaffRequestDetailResponse.VerificationSummary(
                                rs.getString("document_status"),
                                rs.getString("identity_status"),
                                rs.getString("face_match_status"),
                                rs.getString("income_status"),
                                rs.getBigDecimal("verified_monthly_income"),
                                rs.getString("kyc_status"),
                                rs.getString("aml_status"),
                                rs.getBoolean("fraud_flag"),
                                rs.getString("verification_note"),
                                toInstant(rs.getTimestamp("verified_at")));
                    }

                    StaffRequestDetailResponse.RiskAssessmentSummary riskSummary = null;
                    if (rs.getObject("credit_risk_score") != null) {
                        riskSummary = new StaffRequestDetailResponse.RiskAssessmentSummary(
                                rs.getInt("credit_risk_score"),
                                rs.getInt("fraud_risk_score"),
                                rs.getInt("operational_risk_score"),
                                rs.getString("overall_risk_level"),
                                rs.getString("risk_reasons"),
                                toInstant(rs.getTimestamp("risk_created_at")));
                    }

                    StaffRequestDetailResponse.LoanContractSummary contractSummary = null;
                    if (rs.getObject("contract_id") != null) {
                        contractSummary = new StaffRequestDetailResponse.LoanContractSummary(
                                rs.getLong("contract_id"),
                                rs.getString("contract_status"),
                                rs.getBigDecimal("annual_interest_rate"),
                                rs.getBigDecimal("monthly_payment"),
                                rs.getBigDecimal("total_interest"),
                                toInstant(rs.getTimestamp("contract_created_at")));
                    }

                    StaffRequestDetailResponse.AppointmentSummary appointmentSummary = null;
                    if (rs.getObject("appointment_id") != null) {
                        appointmentSummary = new StaffRequestDetailResponse.AppointmentSummary(
                                rs.getLong("appointment_id"),
                                rs.getLong("appointment_staff_id"),
                                rs.getString("appointment_staff_email"),
                                toInstant(rs.getTimestamp("appointment_scheduled_at")),
                                rs.getString("appointment_location"),
                                rs.getString("appointment_note"),
                                rs.getString("appointment_status"),
                                toInstant(rs.getTimestamp("appointment_created_at")));
                    }

                    return new StaffRequestDetailResponse(
                            rs.getLong("id"),
                            LoanType.valueOf(rs.getString("loan_type")),
                            LoanStatus.valueOf(rs.getString("status")),
                            rs.getBigDecimal("amount"),
                            rs.getInt("term_months"),
                            LoanPurpose.valueOf(rs.getString("purpose")),
                            parseEnum(CollateralType.class, rs.getString("collateral_type")),
                            rs.getString("final_reason"),
                            rs.getBigDecimal("eligible_limit"),
                            rs.getBigDecimal("approved_amount"),
                            (Integer) rs.getObject("approved_term_months"),
                            rs.getBigDecimal("approved_annual_rate"),
                            rs.getBigDecimal("approved_monthly_payment"),
                            rs.getString("decision_policy_version"),
                            rs.getString("intake_note"),
                            toInstant(rs.getTimestamp("created_at")),
                            toInstant(rs.getTimestamp("updated_at")),
                            customerSummary,
                            assignmentSummary,
                            customerProfileSummary,
                            dssSummary,
                            verificationSummary,
                            riskSummary,
                            contractSummary,
                            appointmentSummary,
                            List.of(),
                            List.of());
                },
                loanRequestId).stream().findFirst();
    }

    public int assignCase(Long loanRequestId, Long staffUserId) {
        return jdbcTemplate.update(
                """
                        UPDATE loan_requests
                        SET assigned_staff_user_id = ?,
                            assigned_at = CASE
                                WHEN assigned_staff_user_id IS NULL THEN CURRENT_TIMESTAMP
                                ELSE assigned_at
                            END,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                          AND (assigned_staff_user_id IS NULL OR assigned_staff_user_id = ?)
                        """,
                staffUserId,
                loanRequestId,
                staffUserId);
    }

    public int releaseCase(Long loanRequestId, Long staffUserId) {
        return jdbcTemplate.update(
                """
                        UPDATE loan_requests
                        SET assigned_staff_user_id = NULL,
                            assigned_at = NULL,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                          AND assigned_staff_user_id = ?
                        """,
                loanRequestId,
                staffUserId);
    }

    public Optional<Long> findAssignedStaffUserId(Long loanRequestId) {
        return jdbcTemplate.query(
                "SELECT assigned_staff_user_id FROM loan_requests WHERE id = ?",
                (rs, rowNum) -> (Long) rs.getObject("assigned_staff_user_id"),
                loanRequestId).stream().findFirst();
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
                        toInstant(rs.getTimestamp("created_at"))),
                loanRequestId);
    }

    public int updateFinalDecision(
            Long loanRequestId,
            LoanStatus status,
            String reason,
            java.math.BigDecimal approvedAmount,
            Integer approvedTermMonths,
            java.math.BigDecimal approvedAnnualRate,
            java.math.BigDecimal approvedMonthlyPayment,
            String decisionPolicyVersion) {
        return jdbcTemplate.update(
                """
                        UPDATE loan_requests
                        SET status = ?,
                            final_reason = ?,
                            approved_amount = ?,
                            approved_term_months = ?,
                            approved_annual_rate = ?,
                            approved_monthly_payment = ?,
                            decision_policy_version = ?,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                        """,
                status.name(),
                reason,
                approvedAmount,
                approvedTermMonths,
                approvedAnnualRate,
                approvedMonthlyPayment,
                decisionPolicyVersion,
                loanRequestId);
    }

    public void insertDecisionAudit(
            Long loanRequestId,
            Long staffUserId,
            StaffDecisionAction action,
            String reason) {
        jdbcTemplate.update(
                """
                        INSERT INTO decision_audits (loan_request_id, staff_user_id, action, reason)
                        VALUES (?, ?, ?, ?)
                        """,
                loanRequestId,
                staffUserId,
                action.name(),
                reason);
    }

    public void insertAppointment(
            Long loanRequestId,
            Long customerId,
            Long staffUserId,
            Instant scheduledAt,
            String location,
            String note) {
        jdbcTemplate.update(
                """
                        INSERT INTO loan_appointments (
                            loan_request_id,
                            customer_id,
                            staff_id,
                            scheduled_at,
                            location,
                            note,
                            status
                        )
                        VALUES (?, ?, ?, ?, ?, ?, 'SCHEDULED')
                        """,
                loanRequestId,
                customerId,
                staffUserId,
                Timestamp.from(scheduledAt),
                location,
                note);
    }

    private java.time.Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }

    private <T extends Enum<T>> T parseEnum(Class<T> enumType, String value) {
        return value != null ? Enum.valueOf(enumType, value) : null;
    }
}
