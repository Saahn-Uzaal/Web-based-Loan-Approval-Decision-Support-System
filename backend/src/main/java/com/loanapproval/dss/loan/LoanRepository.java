package com.loanapproval.dss.loan;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
                        rs.getBigDecimal("collateral_value"),
                        LoanStatus.valueOf(rs.getString("status")),
                        rs.getString("final_reason"),
                        rs.getBigDecimal("eligible_limit"),
                        rs.getBigDecimal("approved_amount"),
                        (Integer) rs.getObject("approved_term_months"),
                        rs.getBigDecimal("approved_annual_rate"),
                        rs.getBigDecimal("approved_monthly_payment"),
                        rs.getString("decision_policy_version"),
                        rs.getString("intake_note"),
                        rs.getString("additional_info_request_note"),
                        toInstant(rs.getTimestamp("additional_info_last_requested_at")),
                        toInstant(rs.getTimestamp("additional_info_request_deadline")),
                        (Integer) rs.getObject("additional_info_request_count"),
                        toInstant(rs.getTimestamp("review_deadline_at")),
                        toInstant(rs.getTimestamp("contract_acceptance_deadline_at")),
                        toInstant(rs.getTimestamp("created_at")),
                        toInstant(rs.getTimestamp("updated_at")));

        private static final String LOAN_SELECT_COLUMNS = """
                        id,
                        customer_id,
                        loan_type,
                        amount,
                        term_months,
                        purpose,
                        collateral_type,
                        collateral_value,
                        status,
                        final_reason,
                        eligible_limit,
                        approved_amount,
                        approved_term_months,
                        approved_annual_rate,
                        approved_monthly_payment,
                        decision_policy_version,
                        intake_note,
                        additional_info_request_note,
                        additional_info_last_requested_at,
                        additional_info_request_deadline,
                        additional_info_request_count,
                        review_deadline_at,
                        contract_acceptance_deadline_at,
                        created_at,
                        updated_at
                """;

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
                                                SELECT %s
                                                FROM loan_requests
                                                WHERE customer_id = ?
                                                ORDER BY created_at DESC, id DESC
                                                """.formatted(LOAN_SELECT_COLUMNS),
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
                                                  AND status IN (%s)
                                                """.formatted(statusListSql(LoanApplicationPolicy.BLOCKING_APPLICATION_STATUSES)),
                                Integer.class,
                                customerId);
                return count != null && count > 0;
        }

        public List<LoanRecord> findByCustomerIdPaged(Long customerId, int offset, int limit) {
                return jdbcTemplate.query(
                                """
                                                SELECT %s
                                                FROM loan_requests
                                                WHERE customer_id = ?
                                                ORDER BY created_at DESC, id DESC
                                                LIMIT ? OFFSET ?
                                                """.formatted(LOAN_SELECT_COLUMNS),
                                LOAN_ROW_MAPPER,
                                customerId, limit, offset);
        }

        public Optional<LoanRecord> findOwnedById(Long id, Long customerId) {
                return jdbcTemplate.query(
                                """
                                                SELECT %s
                                                FROM loan_requests
                                                WHERE id = ? AND customer_id = ?
                                                """.formatted(LOAN_SELECT_COLUMNS),
                                LOAN_ROW_MAPPER,
                                id,
                                customerId).stream().findFirst();
        }

        public Optional<LoanRecord> findById(Long id) {
                return jdbcTemplate.query(
                                """
                                                SELECT %s
                                                FROM loan_requests
                                                WHERE id = ?
                                                """.formatted(LOAN_SELECT_COLUMNS),
                                LOAN_ROW_MAPPER,
                                id).stream().findFirst();
        }

        public void updateStatus(Long id, LoanStatus status) {
                jdbcTemplate.update(
                                """
                                                UPDATE loan_requests
                                                SET status = ?,
                                                    review_deadline_at = NULL,
                                                    contract_acceptance_deadline_at = NULL,
                                                    updated_at = CURRENT_TIMESTAMP
                                                WHERE id = ?
                                                """,
                                status.name(),
                                id);
        }

        public int withdrawOwnedApplication(Long id, Long customerId, String reason) {
                return jdbcTemplate.update(
                                """
                                                UPDATE loan_requests
                                                SET status = 'WITHDRAWN',
                                                    final_reason = ?,
                                                    additional_info_request_note = NULL,
                                                    additional_info_request_deadline = NULL,
                                                    review_deadline_at = NULL,
                                                    contract_acceptance_deadline_at = NULL,
                                                    updated_at = CURRENT_TIMESTAMP
                                                WHERE id = ?
                                                  AND customer_id = ?
                                                  AND status IN (%s)
                                                """.formatted(statusListSql(LoanApplicationPolicy.CUSTOMER_WITHDRAWABLE_STATUSES)),
                                reason,
                                id,
                                customerId);
        }

        public int resubmitOwnedApplication(Long id, Long customerId, String reason) {
                return jdbcTemplate.update(
                                """
                                                UPDATE loan_requests
                                                SET status = 'PENDING',
                                                    final_reason = ?,
                                                    additional_info_request_note = NULL,
                                                    additional_info_request_deadline = NULL,
                                                    review_deadline_at = NULL,
                                                    contract_acceptance_deadline_at = NULL,
                                                    updated_at = CURRENT_TIMESTAMP
                                                WHERE id = ?
                                                  AND customer_id = ?
                                                  AND status = 'NEEDS_MORE_INFO'
                                                """,
                                reason,
                                id,
                                customerId);
        }

        public int updateOwnedDraft(
                        Long id,
                        Long customerId,
                        LoanType loanType,
                        BigDecimal amount,
                        Integer termMonths,
                        LoanPurpose purpose,
                        CollateralType collateralType,
                        String intakeNote) {
                return jdbcTemplate.update(
                                """
                                                UPDATE loan_requests
                                                SET loan_type = ?,
                                                    amount = ?,
                                                    term_months = ?,
                                                    purpose = ?,
                                                    collateral_type = ?,
                                                    intake_note = ?,
                                                    updated_at = CURRENT_TIMESTAMP
                                                WHERE id = ?
                                                  AND customer_id = ?
                                                  AND status = 'DRAFT'
                                                """,
                                loanType.name(),
                                amount,
                                termMonths,
                                purpose.name(),
                                collateralType != null ? collateralType.name() : null,
                                intakeNote,
                                id,
                                customerId);
        }

        public int submitOwnedDraftForReview(
                        Long id,
                        Long customerId,
                        LoanType loanType,
                        BigDecimal amount,
                        Integer termMonths,
                        LoanPurpose purpose,
                        CollateralType collateralType,
                        BigDecimal eligibleLimit,
                        String intakeNote) {
                return jdbcTemplate.update(
                                """
                                                UPDATE loan_requests
                                                SET loan_type = ?,
                                                    amount = ?,
                                                    term_months = ?,
                                                    purpose = ?,
                                                    collateral_type = ?,
                                                    status = 'PENDING',
                                                    final_reason = NULL,
                                                    eligible_limit = ?,
                                                    approved_amount = NULL,
                                                    approved_term_months = NULL,
                                                    approved_annual_rate = NULL,
                                                    approved_monthly_payment = NULL,
                                                    decision_policy_version = NULL,
                                                    intake_note = ?,
                                                    additional_info_request_note = NULL,
                                                    additional_info_request_deadline = NULL,
                                                    review_deadline_at = NULL,
                                                    contract_acceptance_deadline_at = NULL,
                                                    updated_at = CURRENT_TIMESTAMP
                                                WHERE id = ?
                                                  AND customer_id = ?
                                                  AND status = 'DRAFT'
                                                """,
                                loanType.name(),
                                amount,
                                termMonths,
                                purpose.name(),
                                collateralType != null ? collateralType.name() : null,
                                eligibleLimit,
                                intakeNote,
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
                                                    contract_acceptance_deadline_at = NULL,
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
                                                SET status = ?,
                                                    final_reason = ?,
                                                    review_deadline_at = NULL,
                                                    contract_acceptance_deadline_at = NULL,
                                                    updated_at = CURRENT_TIMESTAMP
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

        public int requestAdditionalInfo(
                        Long id,
                        String reason,
                        String requestNote,
                        Timestamp deadlineAt) {
                return jdbcTemplate.update(
                                """
                                                UPDATE loan_requests
                                                SET status = 'NEEDS_MORE_INFO',
                                                    final_reason = ?,
                                                    additional_info_request_note = ?,
                                                    additional_info_last_requested_at = CURRENT_TIMESTAMP,
                                                    additional_info_request_deadline = ?,
                                                    additional_info_request_count = COALESCE(additional_info_request_count, 0) + 1,
                                                    approved_amount = NULL,
                                                    approved_term_months = NULL,
                                                    approved_annual_rate = NULL,
                                                    approved_monthly_payment = NULL,
                                                    decision_policy_version = NULL,
                                                    review_deadline_at = NULL,
                                                    contract_acceptance_deadline_at = NULL,
                                                    updated_at = CURRENT_TIMESTAMP
                                                WHERE id = ?
                                """,
                                reason,
                                requestNote,
                                deadlineAt,
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
                                                    additional_info_request_note = NULL,
                                                    additional_info_request_deadline = NULL,
                                                    review_deadline_at = NULL,
                                                    contract_acceptance_deadline_at = NULL,
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

        public List<LoanRecord> findExpiredAdditionalInfoRequests(Timestamp cutoff) {
                return jdbcTemplate.query(
                                """
                                                SELECT %s
                                                FROM loan_requests
                                                WHERE status = 'NEEDS_MORE_INFO'
                                                  AND additional_info_request_deadline IS NOT NULL
                                                  AND additional_info_request_deadline <= ?
                                                ORDER BY additional_info_request_deadline ASC, id ASC
                                                """.formatted(LOAN_SELECT_COLUMNS),
                                LOAN_ROW_MAPPER,
                                cutoff);
        }

        public int updateReviewDeadline(Long id, Timestamp reviewDeadlineAt) {
                return jdbcTemplate.update(
                                """
                                                UPDATE loan_requests
                                                SET review_deadline_at = ?,
                                                    contract_acceptance_deadline_at = NULL,
                                                    updated_at = CURRENT_TIMESTAMP
                                                WHERE id = ?
                                """,
                                reviewDeadlineAt,
                                id);
        }

        public int updateContractAcceptanceDeadline(Long id, Timestamp contractAcceptanceDeadlineAt) {
                return jdbcTemplate.update(
                                """
                                                UPDATE loan_requests
                                                SET review_deadline_at = NULL,
                                                    contract_acceptance_deadline_at = ?,
                                                    updated_at = CURRENT_TIMESTAMP
                                                WHERE id = ?
                                """,
                                contractAcceptanceDeadlineAt,
                                id);
        }

        public int clearSlaDeadlines(Long id) {
                return jdbcTemplate.update(
                                """
                                                UPDATE loan_requests
                                                SET review_deadline_at = NULL,
                                                    contract_acceptance_deadline_at = NULL,
                                                    updated_at = CURRENT_TIMESTAMP
                                                WHERE id = ?
                                """,
                                id);
        }

        public int expireAdditionalInfoRequest(Long id, String reason, Timestamp cutoff) {
                return jdbcTemplate.update(
                                """
                                                UPDATE loan_requests
                                                SET status = 'REJECTED',
                                                    final_reason = ?,
                                                    updated_at = CURRENT_TIMESTAMP
                                                WHERE id = ?
                                                  AND status = 'NEEDS_MORE_INFO'
                                                  AND additional_info_request_deadline IS NOT NULL
                                                  AND additional_info_request_deadline <= ?
                                                """,
                                reason,
                                id,
                                cutoff);
        }

        public List<LoanRecord> findExpiredPendingReviews(Timestamp cutoff) {
                return jdbcTemplate.query(
                                """
                                                SELECT %s
                                                FROM loan_requests
                                                WHERE status = 'PENDING'
                                                  AND review_deadline_at IS NOT NULL
                                                  AND review_deadline_at <= ?
                                                ORDER BY review_deadline_at ASC, id ASC
                                                """.formatted(LOAN_SELECT_COLUMNS),
                                LOAN_ROW_MAPPER,
                                cutoff);
        }

        public int expirePendingReview(Long id, String reason, Timestamp cutoff) {
                return jdbcTemplate.update(
                                """
                                                UPDATE loan_requests
                                                SET status = 'REJECTED',
                                                    final_reason = ?,
                                                    review_deadline_at = NULL,
                                                    contract_acceptance_deadline_at = NULL,
                                                    updated_at = CURRENT_TIMESTAMP
                                                WHERE id = ?
                                                  AND status = 'PENDING'
                                                  AND review_deadline_at IS NOT NULL
                                                  AND review_deadline_at <= ?
                                                """,
                                reason,
                                id,
                                cutoff);
        }

        public List<LoanRecord> findExpiredApprovedAcceptances(Timestamp cutoff) {
                return jdbcTemplate.query(
                                """
                                                SELECT %s
                                                FROM loan_requests
                                                WHERE status = 'APPROVED'
                                                  AND contract_acceptance_deadline_at IS NOT NULL
                                                  AND contract_acceptance_deadline_at <= ?
                                                ORDER BY contract_acceptance_deadline_at ASC, id ASC
                                                """.formatted(LOAN_SELECT_COLUMNS),
                                LOAN_ROW_MAPPER,
                                cutoff);
        }

        public int expireApprovedAcceptance(Long id, String reason, Timestamp cutoff) {
                return jdbcTemplate.update(
                                """
                                                UPDATE loan_requests
                                                SET status = 'REJECTED',
                                                    final_reason = ?,
                                                    review_deadline_at = NULL,
                                                    contract_acceptance_deadline_at = NULL,
                                                    updated_at = CURRENT_TIMESTAMP
                                                WHERE id = ?
                                                  AND status = 'APPROVED'
                                                  AND contract_acceptance_deadline_at IS NOT NULL
                                                  AND contract_acceptance_deadline_at <= ?
                                                """,
                                reason,
                                id,
                                cutoff);
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

        public BigDecimal sumCommittedMonthlyPaymentByCustomerId(Long customerId) {
                BigDecimal total = jdbcTemplate.queryForObject(
                                """
                                                SELECT COALESCE(SUM(approved_monthly_payment), 0)
                                                FROM loan_requests
                                                WHERE customer_id = ?
                                                  AND status IN ('CONTRACTED', 'ACTIVE', 'OVERDUE')
                                                """,
                                BigDecimal.class,
                                customerId);
                return total != null ? total : BigDecimal.ZERO;
        }

        private static java.time.Instant toInstant(Timestamp timestamp) {
                return timestamp != null ? timestamp.toInstant() : null;
        }

        private static String statusListSql(Set<LoanStatus> statuses) {
                return statuses.stream()
                        .map(LoanStatus::name)
                        .map(status -> "'" + status + "'")
                        .collect(java.util.stream.Collectors.joining(", "));
        }

        private static <T extends Enum<T>> T parseEnum(Class<T> enumType, String value) {
                return value != null ? Enum.valueOf(enumType, value) : null;
        }
}
