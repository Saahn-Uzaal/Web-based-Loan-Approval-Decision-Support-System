package com.loanapproval.dss.profile;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerProfileRepository {

    private final JdbcTemplate jdbcTemplate;

    public CustomerProfileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<CustomerProfile> findByUserId(Long userId) {
        return jdbcTemplate.query(
            """
            SELECT user_id, full_name, phone, monthly_income, debt_to_income_ratio, employment_status
            FROM customer_profiles
            WHERE user_id = ?
            """,
            (rs, rowNum) -> new CustomerProfile(
                rs.getLong("user_id"),
                rs.getString("full_name"),
                rs.getString("phone"),
                rs.getBigDecimal("monthly_income"),
                rs.getBigDecimal("debt_to_income_ratio"),
                rs.getString("employment_status")
            ),
            userId
        ).stream().findFirst();
    }

    public void upsert(CustomerProfile profile) {
        jdbcTemplate.update(
            """
            INSERT INTO customer_profiles (user_id, full_name, phone, monthly_income, debt_to_income_ratio, employment_status)
            VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                full_name = VALUES(full_name),
                phone = VALUES(phone),
                monthly_income = VALUES(monthly_income),
                debt_to_income_ratio = VALUES(debt_to_income_ratio),
                employment_status = VALUES(employment_status),
                updated_at = CURRENT_TIMESTAMP
            """,
            profile.userId(),
            profile.fullName(),
            profile.phone(),
            profile.monthlyIncome(),
            profile.debtToIncomeRatio(),
            profile.employmentStatus()
        );
    }
}
