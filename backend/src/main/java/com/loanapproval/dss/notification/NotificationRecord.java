package com.loanapproval.dss.notification;

import java.time.Instant;

public record NotificationRecord(
    Long id,
    Long recipientUserId,
    Long actorUserId,
    String actorEmail,
    NotificationCategory type,
    String title,
    String message,
    String link,
    boolean read,
    Instant createdAt,
    Instant readAt
) {
}
