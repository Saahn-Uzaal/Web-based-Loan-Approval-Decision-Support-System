package com.loanapproval.dss.debt;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerDebtRepository {

    private final JdbcTemplate jdbcTemplate;
    private final SimpleJdbcInsert insertDebt;

    public CustomerDebtRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.insertDebt = new SimpleJdbcInsert(jdbcTemplate)
            .withTableName("customer_debts")
            .usingColumns("customer_id", "debt_type", "monthly_payment", "remaining_balance", "lender_name", "status")
            .usingGeneratedKeyColumns("id");
    }

    public CustomerDebt create(
        Long customerId,
        String debtType,
        BigDecimal monthlyPayment,
        BigDecimal remainingBalance,
        String lenderName
    ) {
        Map<String, Object> values = new HashMap<>();
        values.put("customer_id", customerId);
        values.put("debt_type", debtType);
        values.put("monthly_payment", monthlyPayment);
        values.put("remaining_balance", remainingBalance != null ? remainingBalance : BigDecimal.ZERO);
        values.put("lender_name", lenderName);
        values.put("status", DebtStatus.ACTIVE.name());

        Number id = insertDebt.executeAndReturnKey(values);
        return findOwnedById(id.longValue(), customerId)
            .orElseThrow(() -> new IllegalStateException("Created debt was not found"));
    }

    public List<CustomerDebt> findByCustomerId(Long customerId) {
        return jdbcTemplate.query(
            """
            SELECT
                id,
                customer_id,
                debt_type,
                monthly_payment,
                remaining_balance,
                lender_name,
                status,
                created_at,
                updated_at
            FROM customer_debts
            WHERE customer_id = ? AND status = 'ACTIVE'
            ORDER BY created_at DESC, id DESC
            """,
            (rs, rowNum) -> new CustomerDebt(
                rs.getLong("id"),
                rs.getLong("customer_id"),
                rs.getString("debt_type"),
                rs.getBigDecimal("monthly_payment"),
                rs.getBigDecimal("remaining_balance"),
                rs.getString("lender_name"),
                DebtStatus.valueOf(rs.getString("status")),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at"))
            ),
            customerId
        );
    }

    public Optional<CustomerDebt> findOwnedById(Long id, Long customerId) {
        return jdbcTemplate.query(
            """
            SELECT
                id,
                customer_id,
                debt_type,
                monthly_payment,
                remaining_balance,
                lender_name,
                status,
                created_at,
                updated_at
            FROM customer_debts
            WHERE id = ? AND customer_id = ?
            """,
            (rs, rowNum) -> new CustomerDebt(
                rs.getLong("id"),
                rs.getLong("customer_id"),
                rs.getString("debt_type"),
                rs.getBigDecimal("monthly_payment"),
                rs.getBigDecimal("remaining_balance"),
                rs.getString("lender_name"),
                DebtStatus.valueOf(rs.getString("status")),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at"))
            ),
            id,
            customerId
        ).stream().findFirst();
    }

    public int deleteOwned(Long id, Long customerId) {
        return jdbcTemplate.update(
            """
            DELETE FROM customer_debts
            WHERE id = ? AND customer_id = ?
            """,
            id,
            customerId
        );
    }

    public BigDecimal sumActiveMonthlyDebt(Long customerId) {
        BigDecimal total = jdbcTemplate.queryForObject(
            """
            SELECT COALESCE(SUM(monthly_payment), 0)
            FROM customer_debts
            WHERE customer_id = ? AND status = 'ACTIVE'
            """,
            BigDecimal.class,
            customerId
        );
        return total != null ? total : BigDecimal.ZERO;
    }

    private java.time.Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }
}
