package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.NotificationResponse;
import com.pos_onlineshop.hybrid.security.AuthenticatedActorResolver;
import com.pos_onlineshop.hybrid.services.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * list/unread/unreadCount previously took the target userId as a plain query parameter with no
 * check that it matched the caller - any authenticated user could read any other user's
 * notification inbox by changing the parameter. markRead had no ownership check at all - any
 * authenticated user could mark any other user's notification read by guessing/enumerating its
 * id. All four now resolve the caller's own identity server-side via the shared
 * {@link AuthenticatedActorResolver}.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class NotificationController {

    private final NotificationService notificationService;
    private final AuthenticatedActorResolver actorResolver;

    @GetMapping
    public ResponseEntity<?> list(@AuthenticationPrincipal UserDetails principal) {
        try {
            return ResponseEntity.ok(notificationService.findForUser(actorResolver.requireActingUserId(principal)));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/unread")
    public ResponseEntity<?> unread(@AuthenticationPrincipal UserDetails principal) {
        try {
            return ResponseEntity.ok(notificationService.findUnreadForUser(actorResolver.requireActingUserId(principal)));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/unread-count")
    public ResponseEntity<?> unreadCount(@AuthenticationPrincipal UserDetails principal) {
        try {
            long count = notificationService.unreadCount(actorResolver.requireActingUserId(principal));
            return ResponseEntity.ok(Map.of("count", count));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<?> markRead(@PathVariable Long id, @AuthenticationPrincipal UserDetails principal) {
        try {
            Long callerId = actorResolver.requireActingUserId(principal);
            return ResponseEntity.ok(notificationService.markRead(id, callerId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }
}
