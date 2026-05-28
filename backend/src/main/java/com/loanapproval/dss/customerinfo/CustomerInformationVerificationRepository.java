package com.loanapproval.dss.customerinfo;

import com.loanapproval.dss.customerinfo.dto.StaffCustomerInformationDetailResponse;
import com.loanapproval.dss.customerinfo.dto.StaffCustomerInformationSummaryResponse;
import com.loanapproval.dss.verification.VerificationStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerInformationVerificationRepository {

    private final JdbcTemplate jdbcTemplate;

    public CustomerInformationVerificationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<CustomerInformationVerification> findByCustomerId(Long customerId) {
        return jdbcTemplate.query(
            """
            SELECT
                customer_id,
                status,
                rejection_reason,
                reviewed_by,
                reviewed_at,
                created_at,
                updated_at
            FROM customer_information_verifications
            WHERE customer_id = ?
            """,
            (rs, rowNum) -> new CustomerInformationVerification(
                rs.getLong("customer_id"),
                VerificationStatus.valueOf(rs.getString("status")),
                rs.getString("rejection_reason"),
                (Long) rs.getObject("reviewed_by"),
                toInstant(rs.getTimestamp("reviewed_at")),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at"))
            ),
            customerId
        ).stream().findFirst();
    }

    public void upsertDecision(
        Long customerId,
        VerificationStatus status,
        String rejectionReason,
        Long reviewedBy,
        Instant reviewedAt
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO customer_information_verifications (
                customer_id,
                status,
                rejection_reason,
                reviewed_by,
                reviewed_at
            ) VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                status = VALUES(status),
                rejection_reason = VALUES(rejection_reason),
                reviewed_by = VALUES(reviewed_by),
                reviewed_at = VALUES(reviewed_at),
                updated_at = CURRENT_TIMESTAMP
            """,
            customerId,
            status.name(),
            rejectionReason,
            reviewedBy,
            reviewedAt != null ? Timestamp.from(reviewedAt) : null
        );
    }

    public void markPending(Long customerId) {
        jdbcTemplate.update(
            """
            INSERT INTO customer_information_verifications (
                customer_id,
                status,
                rejection_reason,
                reviewed_by,
                reviewed_at
            ) VALUES (?, 'PENDING', NULL, NULL, NULL)
            ON DUPLICATE KEY UPDATE
                status = 'PENDING',
                rejection_reason = NULL,
                reviewed_by = NULL,
                reviewed_at = NULL,
                updated_at = CURRENT_TIMESTAMP
            """,
            customerId
        );
    }

    public List<StaffCustomerInformationSummaryResponse> findCustomerSummaries(VerificationStatus status) {
        if (status == null) {
            return jdbcTemplate.query(
                """
                SELECT
                    u.id AS customer_id,
                    u.email,
                    cp.full_name,
                    cp.phone,
                    cp.payslip_original_filename,
                    cp.payslip_uploaded_at,
                    cp.user_id IS NOT NULL AS has_profile,
                    COALESCE(civ.status, 'PENDING') AS verification_status,
                    civ.rejection_reason,
                    civ.reviewed_at
                FROM users u
                LEFT JOIN customer_profiles cp ON cp.user_id = u.id
                LEFT JOIN customer_information_verifications civ ON civ.customer_id = u.id
                WHERE u.role = 'CUSTOMER'
                  AND cp.user_id IS NOT NULL
                  AND cp.identity_number IS NOT NULL
                  AND TRIM(cp.identity_number) <> ''
                  AND cp.payslip_original_filename IS NOT NULL
                  AND TRIM(cp.payslip_original_filename) <> ''
                  AND cp.identity_card_front_original_filename IS NOT NULL
                  AND TRIM(cp.identity_card_front_original_filename) <> ''
                  AND cp.identity_card_back_original_filename IS NOT NULL
                  AND TRIM(cp.identity_card_back_original_filename) <> ''
                ORDER BY
                    CASE COALESCE(civ.status, 'PENDING')
                        WHEN 'PENDING' THEN 0
                        WHEN 'FAILED' THEN 1
                        ELSE 2
                    END,
                    u.created_at DESC,
                    u.id DESC
                """,
                (rs, rowNum) -> new StaffCustomerInformationSummaryResponse(
                    rs.getLong("customer_id"),
                    rs.getString("email"),
                    rs.getString("full_name"),
                    rs.getString("phone"),
                    rs.getString("payslip_original_filename"),
                    toInstant(rs.getTimestamp("payslip_uploaded_at")),
                    rs.getBoolean("has_profile"),
                    VerificationStatus.valueOf(rs.getString("verification_status")),
                    rs.getString("rejection_reason"),
                    toInstant(rs.getTimestamp("reviewed_at"))
                )
            );
        }

        return jdbcTemplate.query(
            """
            SELECT
                u.id AS customer_id,
                u.email,
                cp.full_name,
                cp.phone,
                cp.payslip_original_filename,
                cp.payslip_uploaded_at,
                cp.user_id IS NOT NULL AS has_profile,
                COALESCE(civ.status, 'PENDING') AS verification_status,
                civ.rejection_reason,
                civ.reviewed_at
            FROM users u
            LEFT JOIN customer_profiles cp ON cp.user_id = u.id
            LEFT JOIN customer_information_verifications civ ON civ.customer_id = u.id
            WHERE u.role = 'CUSTOMER'
              AND cp.user_id IS NOT NULL
              AND cp.identity_number IS NOT NULL
              AND TRIM(cp.identity_number) <> ''
              AND cp.payslip_original_filename IS NOT NULL
              AND TRIM(cp.payslip_original_filename) <> ''
              AND cp.identity_card_front_original_filename IS NOT NULL
              AND TRIM(cp.identity_card_front_original_filename) <> ''
              AND cp.identity_card_back_original_filename IS NOT NULL
              AND TRIM(cp.identity_card_back_original_filename) <> ''
              AND COALESCE(civ.status, 'PENDING') = ?
            ORDER BY u.created_at DESC, u.id DESC
            """,
            (rs, rowNum) -> new StaffCustomerInformationSummaryResponse(
                rs.getLong("customer_id"),
                rs.getString("email"),
                rs.getString("full_name"),
                rs.getString("phone"),
                rs.getString("payslip_original_filename"),
                toInstant(rs.getTimestamp("payslip_uploaded_at")),
                rs.getBoolean("has_profile"),
                VerificationStatus.valueOf(rs.getString("verification_status")),
                rs.getString("rejection_reason"),
                toInstant(rs.getTimestamp("reviewed_at"))
            ),
            status.name()
        );
    }

    public Optional<StaffCustomerInformationDetailResponse> findCustomerDetailById(Long customerId) {
        return jdbcTemplate.query(
            """
            SELECT
                u.id AS customer_id,
                u.email,
                u.created_at AS registered_at,
                cp.user_id AS profile_user_id,
                cp.full_name,
                cp.phone,
                cp.identity_number,
                cp.date_of_birth,
                cp.monthly_income,
                cp.verified_monthly_income,
                cp.debt_to_income_ratio,
                cp.bank_account_number,
                cp.bank_name,
                cp.credit_history_score,
                cp.payment_rating,
                cp.payslip_original_filename,
                cp.payslip_file_size,
                cp.payslip_uploaded_at,
                cp.identity_card_front_original_filename,
                cp.identity_card_front_file_size,
                cp.identity_card_front_uploaded_at,
                cp.identity_card_back_original_filename,
                cp.identity_card_back_file_size,
                cp.identity_card_back_uploaded_at,
                COALESCE(civ.status, 'PENDING') AS verification_status,
                civ.rejection_reason,
                civ.reviewed_at,
                reviewer.email AS reviewed_by_email
            FROM users u
            LEFT JOIN customer_profiles cp ON cp.user_id = u.id
            LEFT JOIN customer_information_verifications civ ON civ.customer_id = u.id
            LEFT JOIN users reviewer ON reviewer.id = civ.reviewed_by
            WHERE u.id = ?
              AND u.role = 'CUSTOMER'
            """,
            (rs, rowNum) -> {
                StaffCustomerInformationDetailResponse.ProfileSummary profile = null;
                if (rs.getObject("profile_user_id") != null) {
                    profile = new StaffCustomerInformationDetailResponse.ProfileSummary(
                        rs.getString("full_name"),
                        rs.getString("phone"),
                        rs.getString("identity_number"),
                        rs.getObject("date_of_birth", java.time.LocalDate.class),
                        rs.getBigDecimal("monthly_income"),
                        rs.getBigDecimal("verified_monthly_income"),
                        rs.getBigDecimal("debt_to_income_ratio"),
                        rs.getString("bank_account_number"),
                        rs.getString("bank_name"),
                        (Integer) rs.getObject("credit_history_score"),
                        (Integer) rs.getObject("payment_rating"),
                        null,
                        rs.getString("payslip_original_filename"),
                        (Long) rs.getObject("payslip_file_size"),
                        toInstant(rs.getTimestamp("payslip_uploaded_at")),
                        rs.getString("identity_card_front_original_filename"),
                        (Long) rs.getObject("identity_card_front_file_size"),
                        toInstant(rs.getTimestamp("identity_card_front_uploaded_at")),
                        rs.getString("identity_card_back_original_filename"),
                        (Long) rs.getObject("identity_card_back_file_size"),
                        toInstant(rs.getTimestamp("identity_card_back_uploaded_at"))
                    );
                }

                return new StaffCustomerInformationDetailResponse(
                    rs.getLong("customer_id"),
                    rs.getString("email"),
                    toInstant(rs.getTimestamp("registered_at")),
                    VerificationStatus.valueOf(rs.getString("verification_status")),
                    rs.getString("rejection_reason"),
                    rs.getString("reviewed_by_email"),
                    toInstant(rs.getTimestamp("reviewed_at")),
                    profile
                );
            },
            customerId
        ).stream().findFirst();
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }
}
