package com.loanapproval.dss.repayment;

import com.loanapproval.dss.repayment.PaymentProofStorageService.StoredPaymentProof;
import com.loanapproval.dss.repayment.dto.StaffPaymentConfirmationSummaryResponse;
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
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentConfirmationRepository {

    private final JdbcTemplate jdbcTemplate;
    private final SimpleJdbcInsert insertConfirmation;

    public PaymentConfirmationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.insertConfirmation = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("payment_confirmation_requests")
                .usingColumns(
                        "loan_request_id",
                        "customer_id",
                        "expected_amount_due",
                        "expected_outstanding_amount",
                        "expected_installment_number",
                        "expected_due_date",
                        "proof_original_filename",
                        "proof_storage_name",
                        "proof_content_type",
                        "proof_file_size",
                        "customer_note",
                        "status")
                .usingGeneratedKeyColumns("id");
    }

    public PaymentConfirmationRequestRecord create(
            Long loanRequestId,
            Long customerId,
            BigDecimal expectedAmountDue,
            BigDecimal expectedOutstandingAmount,
            Integer expectedInstallmentNumber,
            LocalDate expectedDueDate,
            StoredPaymentProof proof,
            String customerNote) {
        Map<String, Object> values = new HashMap<>();
        values.put("loan_request_id", loanRequestId);
        values.put("customer_id", customerId);
        values.put("expected_amount_due", expectedAmountDue);
        values.put("expected_outstanding_amount", expectedOutstandingAmount);
        values.put("expected_installment_number", expectedInstallmentNumber);
        values.put("expected_due_date", Date.valueOf(expectedDueDate));
        values.put("proof_original_filename", proof.originalFileName());
        values.put("proof_storage_name", proof.storageName());
        values.put("proof_content_type", proof.contentType());
        values.put("proof_file_size", proof.fileSize());
        values.put("customer_note", customerNote);
        values.put("status", PaymentConfirmationStatus.PENDING_REVIEW.name());

        Number id = insertConfirmation.executeAndReturnKey(values);
        return findById(id.longValue())
                .orElseThrow(() -> new IllegalStateException("Created payment confirmation was not found"));
    }

    public boolean existsPendingByLoanRequestAndCustomer(Long loanRequestId, Long customerId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM payment_confirmation_requests
                WHERE loan_request_id = ?
                  AND customer_id = ?
                  AND status = 'PENDING_REVIEW'
                """,
                Integer.class,
                loanRequestId,
                customerId);
        return count != null && count > 0;
    }

    public List<PaymentConfirmationRequestRecord> findByCustomerId(Long customerId) {
        return jdbcTemplate.query(
                """
                SELECT
                    id,
                    loan_request_id,
                    customer_id,
                    expected_amount_due,
                    expected_outstanding_amount,
                    expected_installment_number,
                    expected_due_date,
                    proof_original_filename,
                    proof_storage_name,
                    proof_content_type,
                    proof_file_size,
                    customer_note,
                    status,
                    reviewed_by,
                    reviewed_at,
                    confirmed_amount,
                    confirmed_paid_at,
                    bank_transaction_code,
                    staff_note,
                    rejection_reason,
                    repayment_id,
                    created_at,
                    updated_at
                FROM payment_confirmation_requests
                WHERE customer_id = ?
                ORDER BY created_at DESC, id DESC
                """,
                this::mapRecord,
                customerId);
    }

    public Optional<PaymentConfirmationRequestRecord> findByIdAndCustomerId(Long id, Long customerId) {
        return jdbcTemplate.query(
                """
                SELECT
                    id,
                    loan_request_id,
                    customer_id,
                    expected_amount_due,
                    expected_outstanding_amount,
                    expected_installment_number,
                    expected_due_date,
                    proof_original_filename,
                    proof_storage_name,
                    proof_content_type,
                    proof_file_size,
                    customer_note,
                    status,
                    reviewed_by,
                    reviewed_at,
                    confirmed_amount,
                    confirmed_paid_at,
                    bank_transaction_code,
                    staff_note,
                    rejection_reason,
                    repayment_id,
                    created_at,
                    updated_at
                FROM payment_confirmation_requests
                WHERE id = ? AND customer_id = ?
                """,
                this::mapRecord,
                id,
                customerId).stream().findFirst();
    }

    public Optional<PaymentConfirmationRequestRecord> findById(Long id) {
        return jdbcTemplate.query(
                """
                SELECT
                    id,
                    loan_request_id,
                    customer_id,
                    expected_amount_due,
                    expected_outstanding_amount,
                    expected_installment_number,
                    expected_due_date,
                    proof_original_filename,
                    proof_storage_name,
                    proof_content_type,
                    proof_file_size,
                    customer_note,
                    status,
                    reviewed_by,
                    reviewed_at,
                    confirmed_amount,
                    confirmed_paid_at,
                    bank_transaction_code,
                    staff_note,
                    rejection_reason,
                    repayment_id,
                    created_at,
                    updated_at
                FROM payment_confirmation_requests
                WHERE id = ?
                """,
                this::mapRecord,
                id).stream().findFirst();
    }

    public List<StaffPaymentConfirmationSummaryResponse> findSummaries(PaymentConfirmationStatus status) {
        if (status == null) {
            return jdbcTemplate.query(
                    """
                    SELECT
                        pcr.id,
                        pcr.loan_request_id,
                        pcr.customer_id,
                        u.email AS customer_email,
                        cp.full_name AS customer_name,
                        pcr.expected_amount_due,
                        pcr.expected_outstanding_amount,
                        pcr.expected_installment_number,
                        pcr.expected_due_date,
                        pcr.status,
                        pcr.created_at,
                        pcr.reviewed_at
                    FROM payment_confirmation_requests pcr
                    INNER JOIN users u ON u.id = pcr.customer_id
                    LEFT JOIN customer_profiles cp ON cp.user_id = pcr.customer_id
                    ORDER BY
                        CASE pcr.status
                            WHEN 'PENDING_REVIEW' THEN 0
                            WHEN 'REJECTED' THEN 1
                            ELSE 2
                        END,
                        pcr.created_at DESC,
                        pcr.id DESC
                    """,
                    this::mapSummary);
        }

        return jdbcTemplate.query(
                """
                SELECT
                    pcr.id,
                    pcr.loan_request_id,
                    pcr.customer_id,
                    u.email AS customer_email,
                    cp.full_name AS customer_name,
                    pcr.expected_amount_due,
                    pcr.expected_outstanding_amount,
                    pcr.expected_installment_number,
                    pcr.expected_due_date,
                    pcr.status,
                    pcr.created_at,
                    pcr.reviewed_at
                FROM payment_confirmation_requests pcr
                INNER JOIN users u ON u.id = pcr.customer_id
                LEFT JOIN customer_profiles cp ON cp.user_id = pcr.customer_id
                WHERE pcr.status = ?
                ORDER BY pcr.created_at DESC, pcr.id DESC
                """,
                this::mapSummary,
                status.name());
    }

    public int markConfirmed(
            Long id,
            Long reviewedBy,
            Instant reviewedAt,
            BigDecimal confirmedAmount,
            Instant confirmedPaidAt,
            String bankTransactionCode,
            String staffNote,
            Long repaymentId) {
        return jdbcTemplate.update(
                """
                UPDATE payment_confirmation_requests
                SET status = 'CONFIRMED',
                    reviewed_by = ?,
                    reviewed_at = ?,
                    confirmed_amount = ?,
                    confirmed_paid_at = ?,
                    bank_transaction_code = ?,
                    staff_note = ?,
                    rejection_reason = NULL,
                    repayment_id = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND status = 'PENDING_REVIEW'
                """,
                reviewedBy,
                toTimestamp(reviewedAt),
                confirmedAmount,
                toTimestamp(confirmedPaidAt),
                bankTransactionCode,
                staffNote,
                repaymentId,
                id);
    }

    public int markRejected(
            Long id,
            Long reviewedBy,
            Instant reviewedAt,
            String staffNote,
            String rejectionReason) {
        return jdbcTemplate.update(
                """
                UPDATE payment_confirmation_requests
                SET status = 'REJECTED',
                    reviewed_by = ?,
                    reviewed_at = ?,
                    confirmed_amount = NULL,
                    confirmed_paid_at = NULL,
                    bank_transaction_code = NULL,
                    staff_note = ?,
                    rejection_reason = ?,
                    repayment_id = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND status = 'PENDING_REVIEW'
                """,
                reviewedBy,
                toTimestamp(reviewedAt),
                staffNote,
                rejectionReason,
                id);
    }

    private PaymentConfirmationRequestRecord mapRecord(ResultSet rs, int rowNum) throws SQLException {
        return new PaymentConfirmationRequestRecord(
                rs.getLong("id"),
                rs.getLong("loan_request_id"),
                rs.getLong("customer_id"),
                rs.getBigDecimal("expected_amount_due"),
                rs.getBigDecimal("expected_outstanding_amount"),
                rs.getInt("expected_installment_number"),
                rs.getDate("expected_due_date").toLocalDate(),
                rs.getString("proof_original_filename"),
                rs.getString("proof_storage_name"),
                rs.getString("proof_content_type"),
                rs.getLong("proof_file_size"),
                rs.getString("customer_note"),
                PaymentConfirmationStatus.valueOf(rs.getString("status")),
                (Long) rs.getObject("reviewed_by"),
                toInstant(rs.getTimestamp("reviewed_at")),
                rs.getBigDecimal("confirmed_amount"),
                toInstant(rs.getTimestamp("confirmed_paid_at")),
                rs.getString("bank_transaction_code"),
                rs.getString("staff_note"),
                rs.getString("rejection_reason"),
                (Long) rs.getObject("repayment_id"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at")));
    }

    private StaffPaymentConfirmationSummaryResponse mapSummary(ResultSet rs, int rowNum) throws SQLException {
        return new StaffPaymentConfirmationSummaryResponse(
                rs.getLong("id"),
                rs.getLong("loan_request_id"),
                rs.getLong("customer_id"),
                rs.getString("customer_email"),
                rs.getString("customer_name"),
                rs.getBigDecimal("expected_amount_due"),
                rs.getBigDecimal("expected_outstanding_amount"),
                rs.getInt("expected_installment_number"),
                rs.getDate("expected_due_date").toLocalDate(),
                PaymentConfirmationStatus.valueOf(rs.getString("status")),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("reviewed_at")));
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant != null ? Timestamp.from(instant) : null;
    }
}
