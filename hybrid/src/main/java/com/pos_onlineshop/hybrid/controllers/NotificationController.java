package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.NotificationResponse;
import com.pos_onlineshop.hybrid.services.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public List<NotificationResponse> list(@RequestParam Long userId) {
        return notificationService.findForUser(userId);
    }

    @GetMapping("/unread")
    public List<NotificationResponse> unread(@RequestParam Long userId) {
        return notificationService.findUnreadForUser(userId);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@RequestParam Long userId) {
        return Map.of("count", notificationService.unreadCount(userId));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<?> markRead(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(notificationService.markRead(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
