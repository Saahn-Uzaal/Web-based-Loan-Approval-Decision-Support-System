package com.loanapproval.dss.notification;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationRepository {

    private static final RowMapper<NotificationRecord> NOTIFICATION_ROW_MAPPER = (rs, rowNum) -> new NotificationRecord(
        rs.getLong("id"),
        rs.getLong("recipient_user_id"),
        (Long) rs.getObject("actor_user_id"),
        rs.getString("actor_email"),
        NotificationCategory.valueOf(rs.getString("type")),
        rs.getString("title"),
        rs.getString("message"),
        rs.getString("link"),
        rs.getBoolean("is_read"),
        toInstant(rs.getTimestamp("created_at")),
        toInstant(rs.getTimestamp("read_at"))
    );

    private final JdbcTemplate jdbcTemplate;

    public NotificationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void createBatch(
        List<Long> recipientUserIds,
        Long actorUserId,
        NotificationCategory type,
        String title,
        String message,
        String link
    ) {
        if (recipientUserIds == null || recipientUserIds.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(
            """
            INSERT INTO notifications (
                recipient_user_id,
                actor_user_id,
                type,
                title,
                message,
                link,
                is_read,
                read_at
            ) VALUES (?, ?, ?, ?, ?, ?, FALSE, NULL)
            """,
            recipientUserIds,
            recipientUserIds.size(),
            (PreparedStatement ps, Long recipientUserId) -> {
                ps.setLong(1, recipientUserId);
                if (actorUserId != null) {
                    ps.setLong(2, actorUserId);
                } else {
                    ps.setNull(2, Types.BIGINT);
                }
                ps.setString(3, type.name());
                ps.setString(4, title);
                ps.setString(5, message);
                if (link != null) {
                    ps.setString(6, link);
                } else {
                    ps.setNull(6, Types.VARCHAR);
                }
            }
        );
    }

    public List<NotificationRecord> findLatestByRecipientUserId(Long recipientUserId, int limit) {
        return jdbcTemplate.query(
            """
            SELECT
                n.id,
                n.recipient_user_id,
                n.actor_user_id,
                actor.email AS actor_email,
                n.type,
                n.title,
                n.message,
                n.link,
                n.is_read,
                n.created_at,
                n.read_at
            FROM notifications n
            LEFT JOIN users actor ON actor.id = n.actor_user_id
            WHERE n.recipient_user_id = ?
            ORDER BY n.created_at DESC, n.id DESC
            LIMIT ?
            """,
            NOTIFICATION_ROW_MAPPER,
            recipientUserId,
            limit
        );
    }

    public long countUnreadByRecipientUserId(Long recipientUserId) {
        Long count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM notifications
            WHERE recipient_user_id = ?
              AND is_read = FALSE
            """,
            Long.class,
            recipientUserId
        );
        return count != null ? count : 0L;
    }

    public int markAsRead(Long recipientUserId, Long notificationId) {
        return jdbcTemplate.update(
            """
            UPDATE notifications
            SET is_read = TRUE,
                read_at = COALESCE(read_at, CURRENT_TIMESTAMP)
            WHERE id = ?
              AND recipient_user_id = ?
            """,
            notificationId,
            recipientUserId
        );
    }

    public int markAllAsRead(Long recipientUserId) {
        return jdbcTemplate.update(
            """
            UPDATE notifications
            SET is_read = TRUE,
                read_at = COALESCE(read_at, CURRENT_TIMESTAMP)
            WHERE recipient_user_id = ?
              AND is_read = FALSE
            """,
            recipientUserId
        );
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }
}
