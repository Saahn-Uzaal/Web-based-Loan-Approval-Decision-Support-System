package com.loanapproval.dss.profile;

import java.math.BigDecimal;
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
            SELECT
                user_id,
                full_name,
                phone,
                date_of_birth,
                monthly_income,
                verified_monthly_income,
                debt_to_income_ratio,
                employment_status,
                employment_start_date,
                credit_history_score,
                payment_rating,
                payslip_original_filename,
                payslip_storage_name,
                payslip_content_type,
                payslip_file_size,
                payslip_uploaded_at
            FROM customer_profiles
            WHERE user_id = ?
            """,
            (rs, rowNum) -> new CustomerProfile(
                rs.getLong("user_id"),
                rs.getString("full_name"),
                rs.getString("phone"),
                rs.getObject("date_of_birth", java.time.LocalDate.class),
                rs.getBigDecimal("monthly_income"),
                rs.getBigDecimal("verified_monthly_income"),
                rs.getBigDecimal("debt_to_income_ratio"),
                rs.getString("employment_status"),
                rs.getObject("employment_start_date", java.time.LocalDate.class),
                (Integer) rs.getObject("credit_history_score"),
                (Integer) rs.getObject("payment_rating"),
                rs.getString("payslip_original_filename"),
                rs.getString("payslip_storage_name"),
                rs.getString("payslip_content_type"),
                (Long) rs.getObject("payslip_file_size"),
                rs.getTimestamp("payslip_uploaded_at") != null ? rs.getTimestamp("payslip_uploaded_at").toInstant() : null
            ),
            userId
        ).stream().findFirst();
    }

    public void upsert(CustomerProfile profile) {
        jdbcTemplate.update(
            """
            INSERT INTO customer_profiles (
                user_id,
                full_name,
                phone,
                date_of_birth,
                monthly_income,
                debt_to_income_ratio,
                employment_status,
                employment_start_date,
                credit_history_score,
                payslip_original_filename,
                payslip_storage_name,
                payslip_content_type,
                payslip_file_size,
                payslip_uploaded_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                full_name = VALUES(full_name),
                phone = VALUES(phone),
                date_of_birth = VALUES(date_of_birth),
                monthly_income = VALUES(monthly_income),
                debt_to_income_ratio = VALUES(debt_to_income_ratio),
                employment_status = VALUES(employment_status),
                employment_start_date = VALUES(employment_start_date),
                credit_history_score = VALUES(credit_history_score),
                payslip_original_filename = VALUES(payslip_original_filename),
                payslip_storage_name = VALUES(payslip_storage_name),
                payslip_content_type = VALUES(payslip_content_type),
                payslip_file_size = VALUES(payslip_file_size),
                payslip_uploaded_at = VALUES(payslip_uploaded_at),
                updated_at = CURRENT_TIMESTAMP
            """,
            profile.userId(),
            profile.fullName(),
            profile.phone(),
            profile.dateOfBirth(),
            profile.monthlyIncome(),
            profile.debtToIncomeRatio(),
            profile.employmentStatus(),
            profile.employmentStartDate(),
            profile.creditHistoryScore(),
            profile.payslipOriginalFilename(),
            profile.payslipStorageName(),
            profile.payslipContentType(),
            profile.payslipFileSize(),
            profile.payslipUploadedAt()
        );
    }

    public Optional<Integer> findPaymentRatingByUserId(Long userId) {
        var results = jdbcTemplate.query(
            """
            SELECT payment_rating
            FROM customer_profiles
            WHERE user_id = ?
            """,
            (rs, rowNum) -> rs.getInt("payment_rating"),
            userId
        );
        if (results.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(results.get(0));
    }

    public Optional<Integer> adjustPaymentRating(Long userId, int delta) {
        int updatedRows = jdbcTemplate.update(
            """
            UPDATE customer_profiles
            SET payment_rating = LEAST(100, GREATEST(-100, payment_rating + ?)),
                updated_at = CURRENT_TIMESTAMP
            WHERE user_id = ?
            """,
            delta,
            userId
        );

        if (updatedRows == 0) {
            return Optional.empty();
        }
        return findPaymentRatingByUserId(userId);
    }

    public Optional<BigDecimal> findEffectiveMonthlyIncomeByUserId(Long userId) {
        var results = jdbcTemplate.query(
            """
            SELECT COALESCE(NULLIF(verified_monthly_income, 0), monthly_income) AS effective_monthly_income
            FROM customer_profiles
            WHERE user_id = ?
            """,
            (rs, rowNum) -> rs.getBigDecimal("effective_monthly_income"),
            userId
        );
        if (results.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(results.get(0));
    }

    public void updateDebtToIncomeRatio(Long userId, BigDecimal debtToIncomeRatio) {
        jdbcTemplate.update(
            """
            UPDATE customer_profiles
            SET debt_to_income_ratio = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE user_id = ?
            """,
            debtToIncomeRatio,
            userId
        );
    }

    public void updateVerifiedMonthlyIncome(Long userId, BigDecimal verifiedMonthlyIncome) {
        jdbcTemplate.update(
            """
            UPDATE customer_profiles
            SET verified_monthly_income = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE user_id = ?
            """,
            verifiedMonthlyIncome,
            userId
        );
    }

    public void clearVerifiedMonthlyIncome(Long userId) {
        jdbcTemplate.update(
            """
            UPDATE customer_profiles
            SET verified_monthly_income = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE user_id = ?
            """,
            userId
        );
    }
}
