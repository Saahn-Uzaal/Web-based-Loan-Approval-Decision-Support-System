package com.loanapproval.dss.auth;

import com.loanapproval.dss.shared.Role;
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
            .usingColumns("email", "password_hash", "role")
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
            "SELECT id, email, password_hash, role FROM users WHERE email = ?",
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
            "SELECT id, email, password_hash, role FROM users WHERE id = ?",
            (rs, rowNum) -> new UserAccount(
                rs.getLong("id"),
                rs.getString("email"),
                rs.getString("password_hash"),
                Role.valueOf(rs.getString("role"))
            ),
            id
        ).stream().findFirst();
    }

    public UserAccount create(String email, String passwordHash, Role role) {
        Number id = insertUser.executeAndReturnKey(
            java.util.Map.of(
                "email", email,
                "password_hash", passwordHash,
                "role", role.name()
            )
        );
        return new UserAccount(id.longValue(), email, passwordHash, role);
    }

    public int updateEmailAndPassword(Long id, String email, String passwordHash) {
        return jdbcTemplate.update(
            "UPDATE users SET email = ?, password_hash = ? WHERE id = ?",
            email,
            passwordHash,
            id
        );
    }
}
