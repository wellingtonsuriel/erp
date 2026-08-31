package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.NotificationResponse;
import com.pos_onlineshop.hybrid.services.NotificationService;
import com.pos_onlineshop.hybrid.services.UserAccountService;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * All endpoints here act on the caller's own notifications only - the acting user is always
 * resolved from the authenticated principal, never from a client-supplied id, so one account
 * can never read or modify another account's notifications.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserAccountService userAccountService;

    @GetMapping
    public List<NotificationResponse> list(@AuthenticationPrincipal UserDetails userDetails) {
        return notificationService.findForUser(currentUserId(userDetails));
    }

    @GetMapping("/unread")
    public List<NotificationResponse> unread(@AuthenticationPrincipal UserDetails userDetails) {
        return notificationService.findUnreadForUser(currentUserId(userDetails));
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal UserDetails userDetails) {
        return Map.of("count", notificationService.unreadCount(currentUserId(userDetails)));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<?> markRead(@PathVariable Long id, @AuthenticationPrincipal UserDetails userDetails) {
        try {
            return ResponseEntity.ok(notificationService.markRead(id, currentUserId(userDetails)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private Long currentUserId(UserDetails userDetails) {
        UserAccount user = userAccountService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + userDetails.getUsername()));
        return user.getId();
    }
}
