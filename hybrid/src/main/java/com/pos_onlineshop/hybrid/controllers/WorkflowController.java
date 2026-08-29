package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.ApprovalDecisionRequest;
import com.pos_onlineshop.hybrid.dtos.ApprovalRequestResponse;
import com.pos_onlineshop.hybrid.dtos.CreateApprovalRequestRequest;
import com.pos_onlineshop.hybrid.services.WorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/approval-requests")
@RequiredArgsConstructor
@Slf4j
public class WorkflowController {

    private final WorkflowService workflowService;

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
    public ResponseEntity<?> create(@Valid @RequestBody CreateApprovalRequestRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(workflowService.requestApproval(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('GL_APPROVE') or hasAuthority('GL_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<?> approve(@PathVariable Long id, @Valid @RequestBody ApprovalDecisionRequest request) {
        return decide(() -> workflowService.approve(id, request.getUserId(), request.getReason()));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('GL_APPROVE') or hasAuthority('GL_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<?> reject(@PathVariable Long id, @Valid @RequestBody ApprovalDecisionRequest request) {
        return decide(() -> workflowService.reject(id, request.getUserId(), request.getReason()));
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
