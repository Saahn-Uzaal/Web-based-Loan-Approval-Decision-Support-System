package com.loanapproval.dss.notification.dto;

import java.util.List;

public record NotificationFeedResponse(
    List<NotificationResponse> items,
    long unreadCount
) {
}
