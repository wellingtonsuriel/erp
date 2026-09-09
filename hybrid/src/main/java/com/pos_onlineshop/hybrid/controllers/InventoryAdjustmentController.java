package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.CreateInventoryAdjustmentRequest;
import com.pos_onlineshop.hybrid.dtos.InventoryAdjustmentResponse;
import com.pos_onlineshop.hybrid.security.AuthenticatedActorResolver;
import com.pos_onlineshop.hybrid.services.InventoryAdjustmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Manual, ad-hoc stock-count corrections against the one authoritative live pool (see
 * InventoryAdjustment's class comment). The acting user is always resolved from the
 * JWT-authenticated principal, never a request field - see AuthenticatedActorResolver.
 */
@RestController
@RequestMapping("/api/inventory-adjustments")
@RequiredArgsConstructor
@Slf4j
public class InventoryAdjustmentController {

    private final InventoryAdjustmentService inventoryAdjustmentService;
    private final AuthenticatedActorResolver actorResolver;

    @GetMapping
    @PreAuthorize("hasAuthority('INVENTORY_VIEW') or hasRole('ADMIN')")
    public List<InventoryAdjustmentResponse> list() {
        return inventoryAdjustmentService.findAll();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('INVENTORY_ADJUST') or hasRole('ADMIN')")
    public ResponseEntity<?> create(@Valid @RequestBody CreateInventoryAdjustmentRequest request,
                                     @AuthenticationPrincipal UserDetails userDetails) {
        Long actingUserId;
        try {
            actingUserId = actorResolver.requireActingUserId(userDetails);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(inventoryAdjustmentService.createAdjustment(request, actingUserId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            log.error("Failed to post inventory adjustment: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", e.getMessage()));
        }
    }
}
