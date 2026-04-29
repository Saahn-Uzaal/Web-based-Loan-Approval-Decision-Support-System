package com.loanapproval.dss.notification;

import com.loanapproval.dss.notification.dto.NotificationFeedResponse;
import com.loanapproval.dss.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public NotificationFeedResponse getFeed(
        Authentication authentication,
        @RequestParam(defaultValue = "20") int limit
    ) {
        AuthenticatedUser user = extractUser(authentication);
        return notificationService.getFeed(user.id(), limit);
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAsRead(
        Authentication authentication,
        @PathVariable("id") Long notificationId
    ) {
        AuthenticatedUser user = extractUser(authentication);
        notificationService.markAsRead(user.id(), notificationId);
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllAsRead(Authentication authentication) {
        AuthenticatedUser user = extractUser(authentication);
        notificationService.markAllAsRead(user.id());
    }

    private AuthenticatedUser extractUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chưa xác thực");
        }
        return user;
    }
}
