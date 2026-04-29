package com.loanapproval.dss.notification.dto;

import com.loanapproval.dss.notification.NotificationCategory;
import java.time.Instant;

public record NotificationResponse(
    Long id,
    NotificationCategory type,
    String actorEmail,
    String title,
    String message,
    String link,
    boolean read,
    Instant createdAt
) {
}
