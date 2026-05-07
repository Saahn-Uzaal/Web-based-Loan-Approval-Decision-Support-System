package com.loanapproval.dss.contract;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

@Repository
public class LoanInstallmentRepository {

    private final JdbcTemplate jdbcTemplate;
    private final SimpleJdbcInsert insertInstallment;

    public LoanInstallmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.insertInstallment = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("loan_installments")
                .usingColumns(
                        "loan_contract_id",
                        "loan_request_id",
                        "customer_id",
                        "installment_number",
                        "due_date",
                        "opening_principal",
                        "scheduled_principal",
                        "scheduled_interest",
                        "scheduled_fee",
                        "scheduled_amount",
                        "paid_principal",
                        "paid_interest",
                        "paid_fee",
                        "paid_amount",
                        "last_paid_at",
                        "status")
                .usingGeneratedKeyColumns("id");
    }

    public long countByContractId(Long loanContractId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM loan_installments WHERE loan_contract_id = ?",
                Long.class,
                loanContractId);
        return count != null ? count : 0L;
    }

    public List<LoanInstallment> findByLoanRequestId(Long loanRequestId) {
        return jdbcTemplate.query(
                """
                SELECT
                    id,
                    loan_contract_id,
                    loan_request_id,
                    customer_id,
                    installment_number,
                    due_date,
                    opening_principal,
                    scheduled_principal,
                    scheduled_interest,
                    scheduled_fee,
                    scheduled_amount,
                    paid_principal,
                    paid_interest,
                    paid_fee,
                    paid_amount,
                    last_paid_at,
                    status,
                    created_at,
                    updated_at
                FROM loan_installments
                WHERE loan_request_id = ?
                ORDER BY installment_number ASC
                """,
                this::mapRecord,
                loanRequestId);
    }

    public List<LoanInstallment> findByContractId(Long loanContractId) {
        return jdbcTemplate.query(
                """
                SELECT
                    id,
                    loan_contract_id,
                    loan_request_id,
                    customer_id,
                    installment_number,
                    due_date,
                    opening_principal,
                    scheduled_principal,
                    scheduled_interest,
                    scheduled_fee,
                    scheduled_amount,
                    paid_principal,
                    paid_interest,
                    paid_fee,
                    paid_amount,
                    last_paid_at,
                    status,
                    created_at,
                    updated_at
                FROM loan_installments
                WHERE loan_contract_id = ?
                ORDER BY installment_number ASC
                """,
                this::mapRecord,
                loanContractId);
    }

    public void create(LoanInstallment installment) {
        Map<String, Object> values = new HashMap<>();
        values.put("loan_contract_id", installment.loanContractId());
        values.put("loan_request_id", installment.loanRequestId());
        values.put("customer_id", installment.customerId());
        values.put("installment_number", installment.installmentNumber());
        values.put("due_date", Date.valueOf(installment.dueDate()));
        values.put("opening_principal", installment.openingPrincipal());
        values.put("scheduled_principal", installment.scheduledPrincipal());
        values.put("scheduled_interest", installment.scheduledInterest());
        values.put("scheduled_fee", installment.scheduledFee());
        values.put("scheduled_amount", installment.scheduledAmount());
        values.put("paid_principal", installment.paidPrincipal());
        values.put("paid_interest", installment.paidInterest());
        values.put("paid_fee", installment.paidFee());
        values.put("paid_amount", installment.paidAmount());
        values.put("last_paid_at", installment.lastPaidAt() != null ? Timestamp.from(installment.lastPaidAt()) : null);
        values.put("status", installment.status().name());
        insertInstallment.execute(values);
    }

    public void resetLedger(Long loanRequestId) {
        jdbcTemplate.update(
                """
                UPDATE loan_installments
                SET paid_principal = 0,
                    paid_interest = 0,
                    paid_fee = 0,
                    paid_amount = 0,
                    last_paid_at = NULL,
                    status = 'PENDING',
                    updated_at = CURRENT_TIMESTAMP
                WHERE loan_request_id = ?
                """,
                loanRequestId);
    }

    public void updateLedgerState(
            Long installmentId,
            BigDecimal paidPrincipal,
            BigDecimal paidInterest,
            BigDecimal paidFee,
            BigDecimal paidAmount,
            Instant lastPaidAt,
            LoanInstallmentStatus status) {
        jdbcTemplate.update(
                """
                UPDATE loan_installments
                SET paid_principal = ?,
                    paid_interest = ?,
                    paid_fee = ?,
                    paid_amount = ?,
                    last_paid_at = ?,
                    status = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                paidPrincipal,
                paidInterest,
                paidFee,
                paidAmount,
                lastPaidAt != null ? Timestamp.from(lastPaidAt) : null,
                status.name(),
                installmentId);
    }

    private LoanInstallment mapRecord(ResultSet rs, int rowNum) throws SQLException {
        return new LoanInstallment(
                rs.getLong("id"),
                rs.getLong("loan_contract_id"),
                rs.getLong("loan_request_id"),
                rs.getLong("customer_id"),
                rs.getInt("installment_number"),
                rs.getDate("due_date").toLocalDate(),
                rs.getBigDecimal("opening_principal"),
                rs.getBigDecimal("scheduled_principal"),
                rs.getBigDecimal("scheduled_interest"),
                rs.getBigDecimal("scheduled_fee"),
                rs.getBigDecimal("scheduled_amount"),
                rs.getBigDecimal("paid_principal"),
                rs.getBigDecimal("paid_interest"),
                rs.getBigDecimal("paid_fee"),
                rs.getBigDecimal("paid_amount"),
                toInstant(rs.getTimestamp("last_paid_at")),
                LoanInstallmentStatus.valueOf(rs.getString("status")),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at")));
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }
}
