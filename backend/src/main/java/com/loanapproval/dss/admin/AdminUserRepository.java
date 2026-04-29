package com.loanapproval.dss.admin;

import com.loanapproval.dss.shared.Role;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminUserRepository {

    private final JdbcTemplate jdbcTemplate;

    public AdminUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ManagedUserRecord> findManagedUsers(Role role) {
        if (role == null) {
            return jdbcTemplate.query(
                """
                SELECT id, email, role, created_at
                FROM users
                WHERE role IN ('CUSTOMER', 'STAFF')
                ORDER BY created_at DESC, id DESC
                """,
                (rs, rowNum) -> new ManagedUserRecord(
                    rs.getLong("id"),
                    rs.getString("email"),
                    Role.valueOf(rs.getString("role")),
                    toInstant(rs.getTimestamp("created_at"))
                )
            );
        }

        return jdbcTemplate.query(
            """
            SELECT id, email, role, created_at
            FROM users
            WHERE role = ?
            ORDER BY created_at DESC, id DESC
            """,
            (rs, rowNum) -> new ManagedUserRecord(
                rs.getLong("id"),
                rs.getString("email"),
                Role.valueOf(rs.getString("role")),
                toInstant(rs.getTimestamp("created_at"))
            ),
            role.name()
        );
    }

    public Optional<ManagedUserRecord> findById(Long id) {
        return jdbcTemplate.query(
            """
            SELECT id, email, role, created_at
            FROM users
            WHERE id = ?
            """,
            (rs, rowNum) -> new ManagedUserRecord(
                rs.getLong("id"),
                rs.getString("email"),
                Role.valueOf(rs.getString("role")),
                toInstant(rs.getTimestamp("created_at"))
            ),
            id
        ).stream().findFirst();
    }

    public int deleteCustomerAndRelations(Long userId) {
        jdbcTemplate.update(
            "DELETE FROM compliance_audit_logs WHERE customer_id = ? OR actor_user_id = ?",
            userId,
            userId
        );
        jdbcTemplate.update(
            "DELETE FROM customer_information_verifications WHERE customer_id = ?",
            userId
        );
        jdbcTemplate.update(
            "DELETE FROM customer_verifications WHERE customer_id = ?",
            userId
        );
        jdbcTemplate.update(
            "DELETE FROM customer_debts WHERE customer_id = ?",
            userId
        );
        jdbcTemplate.update(
            "DELETE FROM loan_repayments WHERE customer_id = ?",
            userId
        );
        jdbcTemplate.update(
            """
            DELETE lrd
            FROM loan_request_documents lrd
            INNER JOIN loan_requests lr ON lrd.loan_request_id = lr.id
            WHERE lr.customer_id = ?
            """,
            userId
        );
        jdbcTemplate.update(
            """
            DELETE sp
            FROM secured_loan_procedures sp
            INNER JOIN loan_requests lr ON sp.loan_request_id = lr.id
            WHERE lr.customer_id = ?
            """,
            userId
        );
        jdbcTemplate.update(
            "DELETE FROM loan_appointments WHERE customer_id = ?",
            userId
        );
        jdbcTemplate.update(
            "DELETE FROM loan_contracts WHERE customer_id = ?",
            userId
        );
        jdbcTemplate.update(
            """
            DELETE ra
            FROM risk_assessments ra
            INNER JOIN loan_requests lr ON ra.loan_request_id = lr.id
            WHERE lr.customer_id = ?
            """,
            userId
        );
        jdbcTemplate.update(
            """
            DELETE dr
            FROM dss_results dr
            INNER JOIN loan_requests lr ON dr.loan_request_id = lr.id
            WHERE lr.customer_id = ?
            """,
            userId
        );
        jdbcTemplate.update(
            """
            DELETE da
            FROM decision_audits da
            INNER JOIN loan_requests lr ON da.loan_request_id = lr.id
            WHERE lr.customer_id = ?
            """,
            userId
        );
        jdbcTemplate.update(
            "DELETE FROM loan_requests WHERE customer_id = ?",
            userId
        );
        jdbcTemplate.update(
            "DELETE FROM customer_profiles WHERE user_id = ?",
            userId
        );
        return jdbcTemplate.update(
            "DELETE FROM users WHERE id = ?",
            userId
        );
    }

    public int deleteStaffAndRelations(Long userId) {
        jdbcTemplate.update(
            "UPDATE customer_information_verifications SET reviewed_by = NULL WHERE reviewed_by = ?",
            userId
        );
        jdbcTemplate.update(
            "UPDATE customer_verifications SET verified_by = NULL WHERE verified_by = ?",
            userId
        );
        jdbcTemplate.update(
            "UPDATE compliance_audit_logs SET actor_user_id = NULL WHERE actor_user_id = ?",
            userId
        );
        jdbcTemplate.update(
            "DELETE FROM loan_appointments WHERE staff_id = ?",
            userId
        );
        jdbcTemplate.update(
            "DELETE FROM secured_loan_procedures WHERE staff_user_id = ?",
            userId
        );
        jdbcTemplate.update(
            "DELETE FROM decision_audits WHERE staff_user_id = ?",
            userId
        );
        return jdbcTemplate.update(
            "DELETE FROM users WHERE id = ?",
            userId
        );
    }

    private java.time.Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }
}
