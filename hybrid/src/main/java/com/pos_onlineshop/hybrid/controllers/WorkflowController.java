package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.ApprovalDecisionRequest;
import com.pos_onlineshop.hybrid.dtos.ApprovalRequestResponse;
import com.pos_onlineshop.hybrid.dtos.CreateApprovalRequestRequest;
import com.pos_onlineshop.hybrid.security.AuthenticatedActorResolver;
import com.pos_onlineshop.hybrid.services.WorkflowService;
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
import java.util.function.Supplier;

/**
 * The audit-attribution fix documented on WorkflowService's class comment: requestedByUserId/
 * decidedBy must be the authenticated caller, never ApprovalDecisionRequest.userId or
 * CreateApprovalRequestRequest.requestedByUserId from the request body - both DTO fields still
 * exist (kept for backward compatibility with any client still sending them) but are ignored,
 * resolved here via the shared {@link AuthenticatedActorResolver}. Pre-existing constraint this
 * doesn't change: ApprovalRequest.requestedBy/decidedBy are UserAccount foreign keys, so a
 * Cashier-model principal (even one satisfying hasRole('ADMIN')) gets a 403 here rather than
 * being recorded as the requester/decider - this workflow has always been UserAccount-only, it
 * just used to fail on a client-supplied ID instead of failing clearly.
 */
@RestController
@RequestMapping("/api/approval-requests")
@RequiredArgsConstructor
@Slf4j
public class WorkflowController {

    private final WorkflowService workflowService;
    private final AuthenticatedActorResolver actorResolver;

    @GetMapping
    @PreAuthorize("hasAuthority('GL_VIEW') or hasRole('ADMIN')")
    public List<ApprovalRequestResponse> list() {
        return workflowService.findAll();
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('GL_VIEW') or hasRole('ADMIN')")
    public List<ApprovalRequestResponse> pending() {
        return workflowService.findPending();
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> create(@Valid @RequestBody CreateApprovalRequestRequest request,
                                     @AuthenticationPrincipal UserDetails principal) {
        try {
            Long requesterId = actorResolver.requireActingUserId(principal);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(workflowService.requestApproval(request, requesterId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('GL_APPROVE') or hasAuthority('GL_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<?> approve(@PathVariable Long id, @Valid @RequestBody ApprovalDecisionRequest request,
                                      @AuthenticationPrincipal UserDetails principal) {
        try {
            Long deciderId = actorResolver.requireActingUserId(principal);
            return decide(() -> workflowService.approve(id, deciderId, request.getReason()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('GL_APPROVE') or hasAuthority('GL_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<?> reject(@PathVariable Long id, @Valid @RequestBody ApprovalDecisionRequest request,
                                     @AuthenticationPrincipal UserDetails principal) {
        try {
            Long deciderId = actorResolver.requireActingUserId(principal);
            return decide(() -> workflowService.reject(id, deciderId, request.getReason()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    private ResponseEntity<?> decide(Supplier<ApprovalRequestResponse> action) {
        try {
            return ResponseEntity.ok(action.get());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            log.warn("Invalid approval request decision: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }
}
