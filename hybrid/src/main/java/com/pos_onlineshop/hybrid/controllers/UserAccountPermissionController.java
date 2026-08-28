package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.GrantAccountingPermissionRequest;
import com.pos_onlineshop.hybrid.enums.AccountingPermission;
import com.pos_onlineshop.hybrid.services.UserAccountPermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Grants/revokes fine-grained AccountingPermission authorities - see the enum's Javadoc.
 * Every endpoint requires USER_ADMIN (or ADMIN, since account administration already
 * implies permission administration) - never delegated further. */
@RestController
@RequestMapping("/api/users/{userId}/accounting-permissions")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN') or hasAuthority('USER_ADMIN')")
public class UserAccountPermissionController {

    private final UserAccountPermissionService userAccountPermissionService;

    @GetMapping
    public ResponseEntity<?> list(@PathVariable Long userId) {
        try {
            return ResponseEntity.ok(userAccountPermissionService.findForUser(userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    public ResponseEntity<?> grant(@PathVariable Long userId, @Valid @RequestBody GrantAccountingPermissionRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(userAccountPermissionService.grant(userId, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{permission}")
    public ResponseEntity<?> revoke(@PathVariable Long userId, @PathVariable AccountingPermission permission) {
        try {
            userAccountPermissionService.revoke(userId, permission);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
