package com.loanapproval.dss.repayment;

import com.loanapproval.dss.contract.LoanContract;
import com.loanapproval.dss.contract.LoanContractStatus;
import com.loanapproval.dss.loan.CollateralType;
import com.loanapproval.dss.loan.LoanPurpose;
import com.loanapproval.dss.loan.LoanRecord;
import com.loanapproval.dss.loan.LoanStatus;
import com.loanapproval.dss.loan.LoanType;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LoanDelinquencyRepository {

    private final JdbcTemplate jdbcTemplate;

    public LoanDelinquencyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<LoanDelinquencyCandidate> findActiveCandidates() {
        return jdbcTemplate.query(
                candidateSql("lc.status = 'ACTIVE' AND lr.status IN ('ACTIVE', 'OVERDUE')"),
                (rs, rowNum) -> mapCandidate(rs));
    }

    public Optional<LoanDelinquencyCandidate> findCandidateByLoanRequestId(Long loanRequestId) {
        return jdbcTemplate.query(
                candidateSql("lc.status = 'ACTIVE' AND lr.id = ? AND lr.status IN ('ACTIVE', 'OVERDUE')"),
                (rs, rowNum) -> mapCandidate(rs),
                loanRequestId).stream().findFirst();
    }

    public LoanDelinquencyRecord upsertOpen(
            Long loanRequestId,
            Long customerId,
            Integer installmentNumber,
            LocalDate dueDate,
            BigDecimal amountDue,
            BigDecimal currentAmountDue,
            int daysPastDue) {
        jdbcTemplate.update(
                """
                        INSERT INTO loan_delinquencies (
                            loan_request_id,
                            customer_id,
                            installment_number,
                            due_date,
                            amount_due,
                            current_amount_due,
                            days_past_due,
                            status,
                            opened_at,
                            last_assessed_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        ON DUPLICATE KEY UPDATE
                            amount_due = VALUES(amount_due),
                            current_amount_due = VALUES(current_amount_due),
                            days_past_due = VALUES(days_past_due),
                            status = 'OPEN',
                            cured_at = NULL,
                            last_assessed_at = CURRENT_TIMESTAMP,
                            updated_at = CURRENT_TIMESTAMP
                        """,
                loanRequestId,
                customerId,
                installmentNumber,
                java.sql.Date.valueOf(dueDate),
                amountDue,
                currentAmountDue,
                daysPastDue);
        return findByLoanAndInstallment(loanRequestId, installmentNumber, dueDate)
                .orElseThrow(() -> new IllegalStateException("Loan delinquency was not found after upsert"));
    }

    public Optional<LoanDelinquencyRecord> findByLoanAndInstallment(
            Long loanRequestId,
            Integer installmentNumber,
            LocalDate dueDate) {
        return jdbcTemplate.query(
                selectDelinquencySql()
                        + """
                        WHERE loan_request_id = ?
                          AND installment_number = ?
                          AND due_date = ?
                        """,
                (rs, rowNum) -> mapDelinquency(rs),
                loanRequestId,
                installmentNumber,
                java.sql.Date.valueOf(dueDate)).stream().findFirst();
    }

    public Optional<LoanDelinquencyRecord> findLatestByLoanAndDueDate(Long loanRequestId, LocalDate dueDate) {
        return jdbcTemplate.query(
                selectDelinquencySql()
                        + """
                        WHERE loan_request_id = ?
                          AND due_date = ?
                        ORDER BY installment_number DESC, id DESC
                        LIMIT 1
                        """,
                (rs, rowNum) -> mapDelinquency(rs),
                loanRequestId,
                java.sql.Date.valueOf(dueDate)).stream().findFirst();
    }

    public List<LoanDelinquencyRecord> findOpenByLoanRequestId(Long loanRequestId) {
        return jdbcTemplate.query(
                selectDelinquencySql()
                        + """
                        WHERE loan_request_id = ?
                          AND status = 'OPEN'
                        ORDER BY due_date ASC, id ASC
                        """,
                (rs, rowNum) -> mapDelinquency(rs),
                loanRequestId);
    }

    public int updateMilestoneProgress(
            Long id,
            int highestMilestone,
            int ratingDelta,
            BigDecimal feeAssessed,
            BigDecimal currentAmountDue) {
        return jdbcTemplate.update(
                """
                        UPDATE loan_delinquencies
                        SET highest_milestone = ?,
                            total_rating_delta = total_rating_delta + ?,
                            total_fee_assessed = total_fee_assessed + ?,
                            current_amount_due = ?,
                            last_assessed_at = CURRENT_TIMESTAMP,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                        """,
                highestMilestone,
                ratingDelta,
                feeAssessed,
                currentAmountDue,
                id);
    }

    public int markCured(Long id, BigDecimal currentAmountDue) {
        return jdbcTemplate.update(
                """
                        UPDATE loan_delinquencies
                        SET status = 'CURED',
                            current_amount_due = ?,
                            cured_at = CURRENT_TIMESTAMP,
                            last_assessed_at = CURRENT_TIMESTAMP,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                          AND status = 'OPEN'
                        """,
                currentAmountDue,
                id);
    }

    public int markOpenOthersCured(Long loanRequestId, Integer activeInstallmentNumber, LocalDate activeDueDate) {
        return jdbcTemplate.update(
                """
                        UPDATE loan_delinquencies
                        SET status = 'CURED',
                            current_amount_due = 0,
                            cured_at = CURRENT_TIMESTAMP,
                            last_assessed_at = CURRENT_TIMESTAMP,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE loan_request_id = ?
                          AND status = 'OPEN'
                          AND NOT (installment_number = ? AND due_date = ?)
                        """,
                loanRequestId,
                activeInstallmentNumber,
                java.sql.Date.valueOf(activeDueDate));
    }

    public int markAllOpenCured(Long loanRequestId) {
        return jdbcTemplate.update(
                """
                        UPDATE loan_delinquencies
                        SET status = 'CURED',
                            current_amount_due = 0,
                            cured_at = CURRENT_TIMESTAMP,
                            last_assessed_at = CURRENT_TIMESTAMP,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE loan_request_id = ?
                          AND status = 'OPEN'
                        """,
                loanRequestId);
    }

    private String candidateSql(String whereClause) {
        return """
                SELECT
                    lr.id AS loan_id,
                    lr.customer_id AS loan_customer_id,
                    lr.loan_type,
                    lr.amount,
                    lr.term_months AS loan_term_months,
                    lr.purpose,
                    lr.collateral_type,
                    lr.collateral_value,
                    lr.status AS loan_status,
                    lr.final_reason,
                    lr.eligible_limit,
                    lr.approved_amount,
                    lr.approved_term_months,
                    lr.approved_annual_rate,
                    lr.approved_monthly_payment,
                    lr.decision_policy_version,
                    lr.intake_note,
                    lr.created_at AS loan_created_at,
                    lr.updated_at AS loan_updated_at,
                    lc.id AS contract_id,
                    lc.loan_request_id AS contract_loan_request_id,
                    lc.customer_id AS contract_customer_id,
                    lc.principal_amount,
                    lc.annual_interest_rate,
                    lc.term_months AS contract_term_months,
                    lc.start_date,
                    lc.end_date,
                    lc.first_payment_date,
                    lc.monthly_payment_day,
                    lc.final_payment_date,
                    lc.monthly_payment,
                    lc.total_interest,
                    lc.status AS contract_status,
                    lc.created_at AS contract_created_at,
                    lc.updated_at AS contract_updated_at
                FROM loan_contracts lc
                INNER JOIN loan_requests lr ON lr.id = lc.loan_request_id
                WHERE %s
                """.formatted(whereClause);
    }

    private String selectDelinquencySql() {
        return """
                SELECT
                    id,
                    loan_request_id,
                    customer_id,
                    installment_number,
                    due_date,
                    amount_due,
                    current_amount_due,
                    days_past_due,
                    highest_milestone,
                    total_rating_delta,
                    total_fee_assessed,
                    status,
                    opened_at,
                    last_assessed_at,
                    cured_at,
                    created_at,
                    updated_at
                FROM loan_delinquencies
                """;
    }

    private LoanDelinquencyCandidate mapCandidate(ResultSet rs) throws SQLException {
        LoanRecord loan = new LoanRecord(
                rs.getLong("loan_id"),
                rs.getLong("loan_customer_id"),
                LoanType.valueOf(rs.getString("loan_type")),
                rs.getBigDecimal("amount"),
                rs.getInt("loan_term_months"),
                LoanPurpose.valueOf(rs.getString("purpose")),
                parseEnum(CollateralType.class, rs.getString("collateral_type")),
                rs.getBigDecimal("collateral_value"),
                LoanStatus.valueOf(rs.getString("loan_status")),
                rs.getString("final_reason"),
                rs.getBigDecimal("eligible_limit"),
                rs.getBigDecimal("approved_amount"),
                (Integer) rs.getObject("approved_term_months"),
                rs.getBigDecimal("approved_annual_rate"),
                rs.getBigDecimal("approved_monthly_payment"),
                rs.getString("decision_policy_version"),
                rs.getString("intake_note"),
                null,
                null,
                null,
                0,
                toInstant(rs.getTimestamp("loan_created_at")),
                toInstant(rs.getTimestamp("loan_updated_at")));
        LoanContract contract = new LoanContract(
                rs.getLong("contract_id"),
                rs.getLong("contract_loan_request_id"),
                rs.getLong("contract_customer_id"),
                rs.getBigDecimal("principal_amount"),
                rs.getBigDecimal("annual_interest_rate"),
                rs.getInt("contract_term_months"),
                rs.getDate("start_date").toLocalDate(),
                rs.getDate("end_date").toLocalDate(),
                toLocalDate(rs.getDate("first_payment_date")),
                rs.getString("monthly_payment_day"),
                toLocalDate(rs.getDate("final_payment_date")),
                rs.getBigDecimal("monthly_payment"),
                rs.getBigDecimal("total_interest"),
                LoanContractStatus.valueOf(rs.getString("contract_status")),
                toInstant(rs.getTimestamp("contract_created_at")),
                toInstant(rs.getTimestamp("contract_updated_at")));
        return new LoanDelinquencyCandidate(loan, contract);
    }

    private LoanDelinquencyRecord mapDelinquency(ResultSet rs) throws SQLException {
        return new LoanDelinquencyRecord(
                rs.getLong("id"),
                rs.getLong("loan_request_id"),
                rs.getLong("customer_id"),
                rs.getInt("installment_number"),
                rs.getDate("due_date").toLocalDate(),
                rs.getBigDecimal("amount_due"),
                rs.getBigDecimal("current_amount_due"),
                rs.getInt("days_past_due"),
                rs.getInt("highest_milestone"),
                rs.getInt("total_rating_delta"),
                rs.getBigDecimal("total_fee_assessed"),
                LoanDelinquencyStatus.valueOf(rs.getString("status")),
                toInstant(rs.getTimestamp("opened_at")),
                toInstant(rs.getTimestamp("last_assessed_at")),
                toInstant(rs.getTimestamp("cured_at")),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at")));
    }

    private static <T extends Enum<T>> T parseEnum(Class<T> enumType, String value) {
        return value != null ? Enum.valueOf(enumType, value) : null;
    }

    private static LocalDate toLocalDate(java.sql.Date value) {
        return value != null ? value.toLocalDate() : null;
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }
}
