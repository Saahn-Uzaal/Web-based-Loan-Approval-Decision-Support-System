package com.loanapproval.dss.verification;

import java.sql.Timestamp;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerVerificationRepository {

    private final JdbcTemplate jdbcTemplate;

    public CustomerVerificationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<CustomerVerification> findByCustomerId(Long customerId) {
        return jdbcTemplate.query(
            """
            SELECT
                customer_id,
                document_status,
                identity_status,
                income_status,
                kyc_status,
                aml_status,
                fraud_flag,
                note,
                verified_by,
                verified_at,
                created_at,
                updated_at
            FROM customer_verifications
            WHERE customer_id = ?
            """,
            (rs, rowNum) -> new CustomerVerification(
                rs.getLong("customer_id"),
                VerificationStatus.valueOf(rs.getString("document_status")),
                VerificationStatus.valueOf(rs.getString("identity_status")),
                VerificationStatus.valueOf(rs.getString("income_status")),
                VerificationStatus.valueOf(rs.getString("kyc_status")),
                VerificationStatus.valueOf(rs.getString("aml_status")),
                rs.getBoolean("fraud_flag"),
                rs.getString("note"),
                (Long) rs.getObject("verified_by"),
                toInstant(rs.getTimestamp("verified_at")),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at"))
            ),
            customerId
        ).stream().findFirst();
    }

    public void upsert(CustomerVerification verification) {
        jdbcTemplate.update(
            """
            INSERT INTO customer_verifications (
                customer_id,
                document_status,
                identity_status,
                income_status,
                kyc_status,
                aml_status,
                fraud_flag,
                note,
                verified_by,
                verified_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                document_status = VALUES(document_status),
                identity_status = VALUES(identity_status),
                income_status = VALUES(income_status),
                kyc_status = VALUES(kyc_status),
                aml_status = VALUES(aml_status),
                fraud_flag = VALUES(fraud_flag),
                note = VALUES(note),
                verified_by = VALUES(verified_by),
                verified_at = VALUES(verified_at),
                updated_at = CURRENT_TIMESTAMP
            """,
            verification.customerId(),
            verification.documentStatus().name(),
            verification.identityStatus().name(),
            verification.incomeStatus().name(),
            verification.kycStatus().name(),
            verification.amlStatus().name(),
            verification.fraudFlag(),
            verification.note(),
            verification.verifiedBy(),
            verification.verifiedAt() != null ? Timestamp.from(verification.verifiedAt()) : null
        );
    }

    public CustomerVerification defaultPending(Long customerId) {
        return new CustomerVerification(
            customerId,
            VerificationStatus.PENDING,
            VerificationStatus.PENDING,
            VerificationStatus.PENDING,
            VerificationStatus.PENDING,
            VerificationStatus.PENDING,
            false,
            null,
            null,
            null,
            null,
            null
        );
    }

    private java.time.Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }
}
