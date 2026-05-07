package com.loanapproval.dss.admin;

import com.loanapproval.dss.shared.Role;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class AdminUserRepository {

    private static final RowMapper<ManagedUserRecord> MANAGED_USER_ROW_MAPPER = (rs, rowNum) -> new ManagedUserRecord(
        rs.getLong("id"),
        rs.getString("email"),
        Role.valueOf(rs.getString("role")),
        toInstant(rs.getTimestamp("created_at"))
    );

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
                  AND disabled_at IS NULL
                ORDER BY created_at DESC, id DESC
                """,
                MANAGED_USER_ROW_MAPPER
            );
        }

        return jdbcTemplate.query(
            """
            SELECT id, email, role, created_at
            FROM users
            WHERE role = ?
              AND disabled_at IS NULL
            ORDER BY created_at DESC, id DESC
            """,
            MANAGED_USER_ROW_MAPPER,
            role.name()
        );
    }

    public long countManagedUsers(Role role) {
        Long count;
        if (role == null) {
            count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE role IN ('CUSTOMER', 'STAFF') AND disabled_at IS NULL",
                Long.class
            );
        } else {
            count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE role = ? AND disabled_at IS NULL",
                Long.class,
                role.name()
            );
        }
        return count != null ? count : 0L;
    }

    public List<ManagedUserRecord> findManagedUsersPaged(Role role, int offset, int limit) {
        if (role == null) {
            return jdbcTemplate.query(
                """
                SELECT id, email, role, created_at
                FROM users
                WHERE role IN ('CUSTOMER', 'STAFF')
                  AND disabled_at IS NULL
                ORDER BY created_at DESC, id DESC
                LIMIT ? OFFSET ?
                """,
                MANAGED_USER_ROW_MAPPER,
                limit,
                offset
            );
        }

        return jdbcTemplate.query(
            """
            SELECT id, email, role, created_at
            FROM users
            WHERE role = ?
              AND disabled_at IS NULL
            ORDER BY created_at DESC, id DESC
            LIMIT ? OFFSET ?
            """,
            MANAGED_USER_ROW_MAPPER,
            role.name(),
            limit,
            offset
        );
    }

    public Optional<ManagedUserRecord> findById(Long id) {
        return jdbcTemplate.query(
            """
            SELECT id, email, role, created_at
            FROM users
            WHERE id = ?
            """,
            MANAGED_USER_ROW_MAPPER,
            id
        ).stream().findFirst();
    }

    /**
     * Soft-delete a customer account by setting disabled_at.
     * All financial records (loans, contracts, repayments, audits) are preserved.
     */
    public int softDeleteCustomer(Long userId) {
        return jdbcTemplate.update(
            "UPDATE users SET disabled_at = CURRENT_TIMESTAMP WHERE id = ? AND disabled_at IS NULL",
            userId
        );
    }

    /**
     * Soft-delete a staff account by setting disabled_at.
     * Unassigns the staff from any loan cases they own, but preserves
     * all decision audits, compliance logs, and verification history.
     */
    public int softDeleteStaff(Long userId) {
        // Unassign any cases currently assigned to this staff member
        jdbcTemplate.update(
            """
            UPDATE loan_requests
            SET assigned_staff_user_id = NULL,
                assigned_at = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE assigned_staff_user_id = ?
            """,
            userId
        );
        return jdbcTemplate.update(
            "UPDATE users SET disabled_at = CURRENT_TIMESTAMP WHERE id = ? AND disabled_at IS NULL",
            userId
        );
    }

    private static java.time.Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }
}

