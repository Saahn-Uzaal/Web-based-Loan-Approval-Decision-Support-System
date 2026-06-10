package com.loanapproval.dss.auth;

import com.loanapproval.dss.shared.Role;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;
    private final SimpleJdbcInsert insertUser;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.insertUser = new SimpleJdbcInsert(jdbcTemplate)
            .withTableName("users")
            .usingColumns("email", "password_hash", "role", "email_verified_at")
            .usingGeneratedKeyColumns("id");
    }

    public boolean existsByEmail(String email) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users WHERE email = ?",
            Integer.class,
            email
        );
        return count != null && count > 0;
    }

    public Optional<UserAccount> findByEmail(String email) {
        return jdbcTemplate.query(
            "SELECT id, email, password_hash, role FROM users WHERE email = ? AND disabled_at IS NULL",
            (rs, rowNum) -> new UserAccount(
                rs.getLong("id"),
                rs.getString("email"),
                rs.getString("password_hash"),
                Role.valueOf(rs.getString("role"))
            ),
            email
        ).stream().findFirst();
    }

    public Optional<UserAccount> findById(Long id) {
        return jdbcTemplate.query(
            "SELECT id, email, password_hash, role FROM users WHERE id = ? AND disabled_at IS NULL",
            (rs, rowNum) -> new UserAccount(
                rs.getLong("id"),
                rs.getString("email"),
                rs.getString("password_hash"),
                Role.valueOf(rs.getString("role"))
            ),
            id
        ).stream().findFirst();
    }

    public List<Long> findIdsByRole(Role role) {
        return jdbcTemplate.query(
            "SELECT id FROM users WHERE role = ? AND disabled_at IS NULL ORDER BY id ASC",
            (rs, rowNum) -> rs.getLong("id"),
            role.name()
        );
    }

    public UserAccount create(String email, String passwordHash, Role role) {
        return create(email, passwordHash, role, true);
    }

    public UserAccount create(String email, String passwordHash, Role role, boolean emailVerified) {
        Map<String, Object> values = new HashMap<>();
        values.put("email", email);
        values.put("password_hash", passwordHash);
        values.put("role", role.name());
        values.put("email_verified_at", emailVerified ? Timestamp.from(Instant.now()) : null);

        Number id = insertUser.executeAndReturnKey(values);
        return new UserAccount(id.longValue(), email, passwordHash, role);
    }

    public boolean isEmailVerified(Long id) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users WHERE id = ? AND disabled_at IS NULL AND email_verified_at IS NOT NULL",
            Integer.class,
            id
        );
        return count != null && count > 0;
    }

    public Optional<UserEmailVerificationRecord> findEmailVerificationByEmail(String email) {
        return jdbcTemplate.query(
            """
                SELECT id, email, role, email_verified_at, verification_email_sent_at
                FROM users
                WHERE email = ? AND disabled_at IS NULL
                """,
            (rs, rowNum) -> new UserEmailVerificationRecord(
                rs.getLong("id"),
                rs.getString("email"),
                Role.valueOf(rs.getString("role")),
                toInstant(rs.getTimestamp("email_verified_at")),
                toInstant(rs.getTimestamp("verification_email_sent_at"))
            ),
            email
        ).stream().findFirst();
    }

    public Optional<UserEmailVerificationRecord> findEmailVerificationById(Long id) {
        return jdbcTemplate.query(
            """
                SELECT id, email, role, email_verified_at, verification_email_sent_at
                FROM users
                WHERE id = ? AND disabled_at IS NULL
                """,
            (rs, rowNum) -> new UserEmailVerificationRecord(
                rs.getLong("id"),
                rs.getString("email"),
                Role.valueOf(rs.getString("role")),
                toInstant(rs.getTimestamp("email_verified_at")),
                toInstant(rs.getTimestamp("verification_email_sent_at"))
            ),
            id
        ).stream().findFirst();
    }

    public int markVerificationEmailSent(Long id, Instant sentAt) {
        return jdbcTemplate.update(
            "UPDATE users SET verification_email_sent_at = ? WHERE id = ? AND disabled_at IS NULL",
            Timestamp.from(sentAt),
            id
        );
    }

    public int markEmailVerified(Long id, Instant verifiedAt) {
        return jdbcTemplate.update(
            """
                UPDATE users
                SET email_verified_at = ?, verification_email_sent_at = NULL
                WHERE id = ? AND disabled_at IS NULL AND email_verified_at IS NULL
                """,
            Timestamp.from(verifiedAt),
            id
        );
    }

    public int updateEmailAndPassword(Long id, String email, String passwordHash) {
        return jdbcTemplate.update(
            "UPDATE users SET email = ?, password_hash = ? WHERE id = ?",
            email,
            passwordHash,
            id
        );
    }

    private Instant toInstant(Timestamp value) {
        return value != null ? value.toInstant() : null;
    }
}
