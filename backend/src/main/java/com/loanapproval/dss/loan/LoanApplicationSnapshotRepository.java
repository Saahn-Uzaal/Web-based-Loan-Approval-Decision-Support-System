package com.loanapproval.dss.loan;

import com.loanapproval.dss.customerinfo.CustomerInformationVerification;
import com.loanapproval.dss.profile.CustomerProfile;
import com.loanapproval.dss.verification.CustomerVerification;
import com.loanapproval.dss.verification.VerificationStatus;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LoanApplicationSnapshotRepository {

    private final JdbcTemplate jdbcTemplate;

    public LoanApplicationSnapshotRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void createIfMissing(
            Long loanRequestId,
            Long customerId,
            CustomerProfile profile,
            BigDecimal activeMonthlyDebt,
            int activeDebtCount,
            CustomerInformationVerification informationVerification,
            CustomerVerification loanVerification) {
        jdbcTemplate.update(
                """
                        INSERT IGNORE INTO loan_application_snapshots (
                            loan_request_id,
                            customer_id,
                            full_name,
                            phone,
                            date_of_birth,
                            declared_monthly_income,
                            verified_monthly_income,
                            debt_to_income_ratio,
                            employment_status,
                            employment_start_date,
                            credit_history_score,
                            payment_rating,
                            active_monthly_debt,
                            active_debt_count,
                            information_verification_status,
                            document_status,
                            identity_status,
                            face_match_status,
                            income_status,
                            kyc_status,
                            aml_status,
                            fraud_flag
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                loanRequestId,
                customerId,
                profile != null ? profile.fullName() : null,
                profile != null ? profile.phone() : null,
                profile != null ? profile.dateOfBirth() : null,
                profile != null ? profile.monthlyIncome() : null,
                profile != null ? profile.verifiedMonthlyIncome() : null,
                profile != null ? profile.debtToIncomeRatio() : null,
                profile != null ? profile.employmentStatus() : null,
                profile != null ? profile.employmentStartDate() : null,
                profile != null ? profile.creditHistoryScore() : null,
                profile != null ? profile.paymentRating() : null,
                activeMonthlyDebt != null ? activeMonthlyDebt : BigDecimal.ZERO,
                Math.max(activeDebtCount, 0),
                statusOrPending(informationVerification != null ? informationVerification.status() : null).name(),
                statusOrPending(loanVerification != null ? loanVerification.documentStatus() : null).name(),
                statusOrPending(loanVerification != null ? loanVerification.identityStatus() : null).name(),
                statusOrPending(loanVerification != null ? loanVerification.faceMatchStatus() : null).name(),
                statusOrPending(loanVerification != null ? loanVerification.incomeStatus() : null).name(),
                statusOrPending(loanVerification != null ? loanVerification.kycStatus() : null).name(),
                statusOrPending(loanVerification != null ? loanVerification.amlStatus() : null).name(),
                loanVerification != null && loanVerification.fraudFlag());
    }

    public Optional<LoanApplicationSnapshot> findByLoanRequestId(Long loanRequestId) {
        return jdbcTemplate.query(
                """
                        SELECT
                            loan_request_id,
                            customer_id,
                            full_name,
                            phone,
                            date_of_birth,
                            declared_monthly_income,
                            verified_monthly_income,
                            debt_to_income_ratio,
                            employment_status,
                            employment_start_date,
                            credit_history_score,
                            payment_rating,
                            active_monthly_debt,
                            active_debt_count,
                            information_verification_status,
                            document_status,
                            identity_status,
                            face_match_status,
                            income_status,
                            kyc_status,
                            aml_status,
                            fraud_flag,
                            snapshot_at
                        FROM loan_application_snapshots
                        WHERE loan_request_id = ?
                        """,
                (rs, rowNum) -> map(rs),
                loanRequestId).stream().findFirst();
    }

    private LoanApplicationSnapshot map(ResultSet rs) throws SQLException {
        return new LoanApplicationSnapshot(
                rs.getLong("loan_request_id"),
                rs.getLong("customer_id"),
                rs.getString("full_name"),
                rs.getString("phone"),
                rs.getObject("date_of_birth", java.time.LocalDate.class),
                rs.getBigDecimal("declared_monthly_income"),
                rs.getBigDecimal("verified_monthly_income"),
                rs.getBigDecimal("debt_to_income_ratio"),
                rs.getString("employment_status"),
                rs.getObject("employment_start_date", java.time.LocalDate.class),
                (Integer) rs.getObject("credit_history_score"),
                (Integer) rs.getObject("payment_rating"),
                rs.getBigDecimal("active_monthly_debt"),
                (Integer) rs.getObject("active_debt_count"),
                VerificationStatus.valueOf(rs.getString("information_verification_status")),
                VerificationStatus.valueOf(rs.getString("document_status")),
                VerificationStatus.valueOf(rs.getString("identity_status")),
                VerificationStatus.valueOf(rs.getString("face_match_status")),
                VerificationStatus.valueOf(rs.getString("income_status")),
                VerificationStatus.valueOf(rs.getString("kyc_status")),
                VerificationStatus.valueOf(rs.getString("aml_status")),
                rs.getBoolean("fraud_flag"),
                toInstant(rs.getTimestamp("snapshot_at")));
    }

    private VerificationStatus statusOrPending(VerificationStatus status) {
        return status != null ? status : VerificationStatus.PENDING;
    }

    private static java.time.Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }
}
