package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.NotificationResponse;
import com.pos_onlineshop.hybrid.services.NotificationService;
import com.pos_onlineshop.hybrid.services.UserAccountService;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
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
 * id. All four now resolve the caller's own identity server-side.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserAccountService userAccountService;

    private UserAccount requireAuthenticatedUser(UserDetails principal) {
        return userAccountService.findByUsername(principal.getUsername())
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated principal " + principal.getUsername() + " is not a UserAccount"));
    }

    @GetMapping
    public ResponseEntity<?> list(@AuthenticationPrincipal UserDetails principal) {
        try {
            return ResponseEntity.ok(notificationService.findForUser(requireAuthenticatedUser(principal).getId()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/unread")
    public ResponseEntity<?> unread(@AuthenticationPrincipal UserDetails principal) {
        try {
            return ResponseEntity.ok(notificationService.findUnreadForUser(requireAuthenticatedUser(principal).getId()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/unread-count")
    public ResponseEntity<?> unreadCount(@AuthenticationPrincipal UserDetails principal) {
        try {
            long count = notificationService.unreadCount(requireAuthenticatedUser(principal).getId());
            return ResponseEntity.ok(Map.of("count", count));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<?> markRead(@PathVariable Long id, @AuthenticationPrincipal UserDetails principal) {
        try {
            UserAccount caller = requireAuthenticatedUser(principal);
            return ResponseEntity.ok(notificationService.markRead(id, caller.getId()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }
}
