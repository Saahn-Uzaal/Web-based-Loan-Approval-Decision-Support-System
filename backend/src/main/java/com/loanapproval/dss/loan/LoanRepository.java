package com.loanapproval.dss.loan;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

@Repository
public class LoanRepository {

        private static final RowMapper<LoanRecord> LOAN_ROW_MAPPER = (rs, rowNum) -> new LoanRecord(
                        rs.getLong("id"),
                        rs.getLong("customer_id"),
                        LoanType.valueOf(rs.getString("loan_type")),
                        rs.getBigDecimal("amount"),
                        rs.getInt("term_months"),
                        LoanPurpose.valueOf(rs.getString("purpose")),
                        parseEnum(CollateralType.class, rs.getString("collateral_type")),
                        LoanStatus.valueOf(rs.getString("status")),
                        rs.getString("final_reason"),
                        rs.getBigDecimal("eligible_limit"),
                        rs.getBigDecimal("approved_amount"),
                        (Integer) rs.getObject("approved_term_months"),
                        rs.getBigDecimal("approved_annual_rate"),
                        rs.getBigDecimal("approved_monthly_payment"),
                        rs.getString("decision_policy_version"),
                        rs.getString("intake_note"),
                        toInstant(rs.getTimestamp("created_at")),
                        toInstant(rs.getTimestamp("updated_at")));

        private final JdbcTemplate jdbcTemplate;
        private final SimpleJdbcInsert insertLoan;

        public LoanRepository(JdbcTemplate jdbcTemplate) {
                this.jdbcTemplate = jdbcTemplate;
                this.insertLoan = new SimpleJdbcInsert(jdbcTemplate)
                                .withTableName("loan_requests")
                                .usingColumns(
                                                "customer_id",
                                                "loan_type",
                                                "amount",
                                                "term_months",
                                                "purpose",
                                                "collateral_type",
                                                "status",
                                                "final_reason",
                                                "eligible_limit",
                                                "intake_note")
                                .usingGeneratedKeyColumns("id");
        }

        public LoanRecord create(
                        Long customerId,
                        LoanType loanType,
                        BigDecimal amount,
                        Integer termMonths,
                        LoanPurpose purpose,
                        CollateralType collateralType,
                        BigDecimal eligibleLimit,
                        String intakeNote) {
                return create(
                                customerId,
                                loanType,
                                amount,
                                termMonths,
                                purpose,
                                collateralType,
                                LoanStatus.PENDING,
                                eligibleLimit,
                                intakeNote);
        }

        public LoanRecord create(
                        Long customerId,
                        LoanType loanType,
                        BigDecimal amount,
                        Integer termMonths,
                        LoanPurpose purpose,
                        CollateralType collateralType,
                        LoanStatus status,
                        BigDecimal eligibleLimit,
                        String intakeNote) {
                Map<String, Object> values = new HashMap<>();
                values.put("customer_id", customerId);
                values.put("loan_type", loanType.name());
                values.put("amount", amount);
                values.put("term_months", termMonths);
                values.put("purpose", purpose.name());
                values.put("collateral_type", collateralType != null ? collateralType.name() : null);
                values.put("status", status.name());
                values.put("final_reason", null);
                values.put("eligible_limit", eligibleLimit);
                values.put("intake_note", intakeNote);

                Number id = insertLoan.executeAndReturnKey(
                                values);
                return findOwnedById(id.longValue(), customerId)
                                .orElseThrow(() -> new IllegalStateException("Created loan request was not found"));
        }

        public List<LoanRecord> findByCustomerId(Long customerId) {
                return jdbcTemplate.query(
                                """
                                                SELECT id, customer_id, loan_type, amount, term_months, purpose, collateral_type,
                                                       status, final_reason, eligible_limit, approved_amount, approved_term_months,
                                                       approved_annual_rate, approved_monthly_payment, decision_policy_version,
                                                       intake_note, created_at, updated_at
                                                FROM loan_requests
                                                WHERE customer_id = ?
                                                ORDER BY created_at DESC, id DESC
                                                """,
                                LOAN_ROW_MAPPER,
                                customerId);
        }

        public long countByCustomerId(Long customerId) {
                Long count = jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM loan_requests WHERE customer_id = ?",
                                Long.class,
                                customerId);
                return count != null ? count : 0L;
        }

        public boolean existsOpenApplicationByCustomerId(Long customerId) {
                Integer count = jdbcTemplate.queryForObject(
                                """
                                                SELECT COUNT(*)
                                                FROM loan_requests
                                                WHERE customer_id = ?
                                                  AND status IN (
                                                      'DRAFT',
                                                      'PENDING',
                                                      'NEEDS_MORE_INFO',
                                                      'APPOINTMENT_SCHEDULED',
                                                      'APPROVED',
                                                      'CONTRACTED',
                                                      'ACTIVE',
                                                      'OVERDUE'
                                                  )
                                                """,
                                Integer.class,
                                customerId);
                return count != null && count > 0;
        }

        public List<LoanRecord> findByCustomerIdPaged(Long customerId, int offset, int limit) {
                return jdbcTemplate.query(
                                """
                                                SELECT id, customer_id, loan_type, amount, term_months, purpose, collateral_type,
                                                       status, final_reason, eligible_limit, approved_amount, approved_term_months,
                                                       approved_annual_rate, approved_monthly_payment, decision_policy_version,
                                                       intake_note, created_at, updated_at
                                                FROM loan_requests
                                                WHERE customer_id = ?
                                                ORDER BY created_at DESC, id DESC
                                                LIMIT ? OFFSET ?
                                                """,
                                LOAN_ROW_MAPPER,
                                customerId, limit, offset);
        }

        public Optional<LoanRecord> findOwnedById(Long id, Long customerId) {
                return jdbcTemplate.query(
                                """
                                                SELECT id, customer_id, loan_type, amount, term_months, purpose, collateral_type,
                                                       status, final_reason, eligible_limit, approved_amount, approved_term_months,
                                                       approved_annual_rate, approved_monthly_payment, decision_policy_version,
                                                       intake_note, created_at, updated_at
                                                FROM loan_requests
                                                WHERE id = ? AND customer_id = ?
                                                """,
                                LOAN_ROW_MAPPER,
                                id,
                                customerId).stream().findFirst();
        }

        public Optional<LoanRecord> findById(Long id) {
                return jdbcTemplate.query(
                                """
                                                SELECT id, customer_id, loan_type, amount, term_months, purpose, collateral_type,
                                                       status, final_reason, eligible_limit, approved_amount, approved_term_months,
                                                       approved_annual_rate, approved_monthly_payment, decision_policy_version,
                                                       intake_note, created_at, updated_at
                                                FROM loan_requests
                                                WHERE id = ?
                                                """,
                                LOAN_ROW_MAPPER,
                                id).stream().findFirst();
        }

        public void updateStatus(Long id, LoanStatus status) {
                jdbcTemplate.update(
                                """
                                                UPDATE loan_requests
                                                SET status = ?, updated_at = CURRENT_TIMESTAMP
                                                WHERE id = ?
                                                """,
                                status.name(),
                                id);
        }

        public int updateOwnedStatusAndReason(Long id, Long customerId, LoanStatus status, String reason) {
                return jdbcTemplate.update(
                                """
                                                UPDATE loan_requests
                                                SET status = ?,
                                                    final_reason = ?,
                                                    updated_at = CURRENT_TIMESTAMP
                                                WHERE id = ?
                                                  AND customer_id = ?
                                                  AND status IN ('DRAFT', 'PENDING', 'NEEDS_MORE_INFO', 'APPOINTMENT_SCHEDULED', 'APPROVED')
                                                """,
                                status.name(),
                                reason,
                                id,
                                customerId);
        }

        public int markAcceptedAndContracted(Long id, Long customerId, String acceptedTermsVersion) {
                return jdbcTemplate.update(
                                """
                                                UPDATE loan_requests
                                                SET status = 'CONTRACTED',
                                                    accepted_at = CURRENT_TIMESTAMP,
                                                    accepted_terms_version = ?,
                                                    updated_at = CURRENT_TIMESTAMP
                                                WHERE id = ?
                                                  AND customer_id = ?
                                                  AND status = 'APPROVED'
                                                """,
                                acceptedTermsVersion,
                                id,
                                customerId);
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
                                id);
        }

        public int updateCollateralValue(Long id, BigDecimal collateralValue) {
                return jdbcTemplate.update(
                                """
                                                UPDATE loan_requests
                                                SET collateral_value = ?,
                                                    updated_at = CURRENT_TIMESTAMP
                                                WHERE id = ?
                                                """,
                                collateralValue,
                                id);
        }

        public int updateFinalReason(Long id, String reason) {
                return jdbcTemplate.update(
                                """
                                                UPDATE loan_requests
                                                SET final_reason = ?,
                                                    updated_at = CURRENT_TIMESTAMP
                                                WHERE id = ?
                                                """,
                                reason,
                                id);
        }

        public int updateDecision(
                        Long id,
                        LoanStatus status,
                        String reason,
                        BigDecimal approvedAmount,
                        Integer approvedTermMonths,
                        BigDecimal approvedAnnualRate,
                        BigDecimal approvedMonthlyPayment,
                        String decisionPolicyVersion) {
                return updateDecision(
                                id,
                                status,
                                reason,
                                null,
                                approvedAmount,
                                approvedTermMonths,
                                approvedAnnualRate,
                                approvedMonthlyPayment,
                                decisionPolicyVersion);
        }

        public int updateDecision(
                        Long id,
                        LoanStatus status,
                        String reason,
                        BigDecimal eligibleLimit,
                        BigDecimal approvedAmount,
                        Integer approvedTermMonths,
                        BigDecimal approvedAnnualRate,
                        BigDecimal approvedMonthlyPayment,
                        String decisionPolicyVersion) {
                return jdbcTemplate.update(
                                """
                                                UPDATE loan_requests
                                                SET status = ?,
                                                    final_reason = ?,
                                                    eligible_limit = COALESCE(?, eligible_limit),
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
                                eligibleLimit,
                                approvedAmount,
                                approvedTermMonths,
                                approvedAnnualRate,
                                approvedMonthlyPayment,
                                decisionPolicyVersion,
                                id);
        }

        public int assignCaseIfUnassignedOrOwned(Long id, Long staffUserId) {
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
                                id,
                                staffUserId);
        }

        public int releaseCaseAssignment(Long id, Long staffUserId) {
                return jdbcTemplate.update(
                                """
                                                UPDATE loan_requests
                                                SET assigned_staff_user_id = NULL,
                                                    assigned_at = NULL,
                                                    updated_at = CURRENT_TIMESTAMP
                                                WHERE id = ?
                                                  AND assigned_staff_user_id = ?
                                                """,
                                id,
                                staffUserId);
        }

        public Optional<Long> findAssignedStaffUserId(Long id) {
                List<Long> values = jdbcTemplate.query(
                                "SELECT assigned_staff_user_id FROM loan_requests WHERE id = ?",
                                (rs, rowNum) -> (Long) rs.getObject("assigned_staff_user_id"),
                                id);
                if (values.isEmpty()) {
                        return Optional.empty();
                }
                return Optional.ofNullable(values.get(0));
        }

        private static java.time.Instant toInstant(Timestamp timestamp) {
                return timestamp != null ? timestamp.toInstant() : null;
        }

        private static <T extends Enum<T>> T parseEnum(Class<T> enumType, String value) {
                return value != null ? Enum.valueOf(enumType, value) : null;
        }
}
