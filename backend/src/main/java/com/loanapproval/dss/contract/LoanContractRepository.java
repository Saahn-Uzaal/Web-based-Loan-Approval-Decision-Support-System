package com.loanapproval.dss.contract;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

@Repository
public class LoanContractRepository {

    private final JdbcTemplate jdbcTemplate;
    private final SimpleJdbcInsert insertLoanContract;

    public LoanContractRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.insertLoanContract = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("loan_contracts")
                .usingColumns(
                        "loan_request_id",
                        "customer_id",
                        "principal_amount",
                        "annual_interest_rate",
                        "term_months",
                        "start_date",
                        "end_date",
                        "first_payment_date",
                        "monthly_payment_day",
                        "final_payment_date",
                        "monthly_payment",
                        "total_interest",
                        "status")
                .usingGeneratedKeyColumns("id");
    }

    public LoanContract create(
            Long loanRequestId,
            Long customerId,
            BigDecimal principalAmount,
            BigDecimal annualInterestRate,
            Integer termMonths,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate firstPaymentDate,
            String monthlyPaymentDay,
            LocalDate finalPaymentDate,
            BigDecimal monthlyPayment,
            BigDecimal totalInterest) {
        return create(
                loanRequestId,
                customerId,
                principalAmount,
                annualInterestRate,
                termMonths,
                startDate,
                endDate,
                firstPaymentDate,
                monthlyPaymentDay,
                finalPaymentDate,
                monthlyPayment,
                totalInterest,
                LoanContractStatus.ACTIVE);
    }

    public LoanContract create(
            Long loanRequestId,
            Long customerId,
            BigDecimal principalAmount,
            BigDecimal annualInterestRate,
            Integer termMonths,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate firstPaymentDate,
            String monthlyPaymentDay,
            LocalDate finalPaymentDate,
            BigDecimal monthlyPayment,
            BigDecimal totalInterest,
            LoanContractStatus status) {
        Map<String, Object> values = new HashMap<>();
        values.put("loan_request_id", loanRequestId);
        values.put("customer_id", customerId);
        values.put("principal_amount", principalAmount);
        values.put("annual_interest_rate", annualInterestRate);
        values.put("term_months", termMonths);
        values.put("start_date", Date.valueOf(startDate));
        values.put("end_date", Date.valueOf(endDate));
        values.put("first_payment_date", toSqlDate(firstPaymentDate));
        values.put("monthly_payment_day", monthlyPaymentDay);
        values.put("final_payment_date", toSqlDate(finalPaymentDate));
        values.put("monthly_payment", monthlyPayment);
        values.put("total_interest", totalInterest);
        values.put("status", status != null ? status.name() : LoanContractStatus.ACTIVE.name());

        Number id = insertLoanContract.executeAndReturnKey(values);
        return findById(id.longValue())
                .orElseThrow(() -> new IllegalStateException("Created loan contract was not found"));
    }

    public Optional<LoanContract> findByLoanRequestId(Long loanRequestId) {
        return jdbcTemplate.query(
                        """
                        SELECT
                            id,
                            loan_request_id,
                            customer_id,
                            principal_amount,
                            annual_interest_rate,
                            term_months,
                            start_date,
                            end_date,
                            first_payment_date,
                            monthly_payment_day,
                            final_payment_date,
                            monthly_payment,
                            total_interest,
                            status,
                            created_at,
                            updated_at
                        FROM loan_contracts
                        WHERE loan_request_id = ?
                        """,
                        (rs, rowNum) -> mapLoanContract(rs),
                        loanRequestId)
                .stream()
                .findFirst();
    }

    public Optional<LoanContract> findByLoanRequestIdAndCustomerId(Long loanRequestId, Long customerId) {
        return jdbcTemplate.query(
                        """
                        SELECT
                            id,
                            loan_request_id,
                            customer_id,
                            principal_amount,
                            annual_interest_rate,
                            term_months,
                            start_date,
                            end_date,
                            first_payment_date,
                            monthly_payment_day,
                            final_payment_date,
                            monthly_payment,
                            total_interest,
                            status,
                            created_at,
                            updated_at
                        FROM loan_contracts
                        WHERE loan_request_id = ? AND customer_id = ?
                        """,
                        (rs, rowNum) -> mapLoanContract(rs),
                        loanRequestId,
                        customerId)
                .stream()
                .findFirst();
    }

    public Optional<LoanContract> findById(Long id) {
        return jdbcTemplate.query(
                        """
                        SELECT
                            id,
                            loan_request_id,
                            customer_id,
                            principal_amount,
                            annual_interest_rate,
                            term_months,
                            start_date,
                            end_date,
                            first_payment_date,
                            monthly_payment_day,
                            final_payment_date,
                            monthly_payment,
                            total_interest,
                            status,
                            created_at,
                            updated_at
                        FROM loan_contracts
                        WHERE id = ?
                        """,
                        (rs, rowNum) -> mapLoanContract(rs),
                        id)
                .stream()
                .findFirst();
    }

    public int updateStatus(Long id, LoanContractStatus status) {
        return jdbcTemplate.update(
                """
                UPDATE loan_contracts
                SET status = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                status.name(),
                id);
    }

    private LoanContract mapLoanContract(ResultSet rs) throws SQLException {
        return new LoanContract(
                rs.getLong("id"),
                rs.getLong("loan_request_id"),
                rs.getLong("customer_id"),
                rs.getBigDecimal("principal_amount"),
                rs.getBigDecimal("annual_interest_rate"),
                rs.getInt("term_months"),
                rs.getDate("start_date").toLocalDate(),
                rs.getDate("end_date").toLocalDate(),
                toLocalDate(rs.getDate("first_payment_date")),
                rs.getString("monthly_payment_day"),
                toLocalDate(rs.getDate("final_payment_date")),
                rs.getBigDecimal("monthly_payment"),
                rs.getBigDecimal("total_interest"),
                LoanContractStatus.valueOf(rs.getString("status")),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at")));
    }

    private Date toSqlDate(LocalDate value) {
        return value != null ? Date.valueOf(value) : null;
    }

    private LocalDate toLocalDate(Date value) {
        return value != null ? value.toLocalDate() : null;
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }
}
