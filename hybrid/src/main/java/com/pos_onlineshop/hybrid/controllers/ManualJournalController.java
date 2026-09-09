package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.*;
import com.pos_onlineshop.hybrid.gl.GLPostingException;
import com.pos_onlineshop.hybrid.security.AuthenticatedActorResolver;
import com.pos_onlineshop.hybrid.services.ManualJournalService;
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
 * create/submit/approve/reject/post's acting-user id must always be the authenticated caller,
 * never CreateManualJournalRequest.createdByUserId from the request body - see
 * ManualJournalService's class comment for why (the same preparer/approver identity-spoofing bug
 * fixed on WorkflowController), resolved here via the shared {@link AuthenticatedActorResolver}.
 * Pre-existing constraint this doesn't change: ManualJournal.createdBy/submittedBy/approvedBy/
 * rejectedBy are UserAccount foreign keys, so a Cashier-model principal gets a 403 here.
 */
@RestController
@RequestMapping("/api/manual-journals")
@RequiredArgsConstructor
@Slf4j
public class ManualJournalController {

    private final ManualJournalService manualJournalService;
    private final AuthenticatedActorResolver actorResolver;

    @GetMapping
    @PreAuthorize("hasAuthority('GL_VIEW') or hasRole('ADMIN')")
    public List<ManualJournalResponse> list() {
        return manualJournalService.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('GL_VIEW') or hasRole('ADMIN')")
    public ResponseEntity<?> get(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(manualJournalService.findById(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('GL_MANUAL_JOURNAL') or hasRole('ADMIN')")
    public ResponseEntity<?> create(@Valid @RequestBody CreateManualJournalRequest request,
                                     @AuthenticationPrincipal UserDetails principal) {
        try {
            Long creatorId = actorResolver.requireActingUserId(principal);
            return ResponseEntity.status(HttpStatus.CREATED).body(manualJournalService.create(request, creatorId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (GLPostingException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('GL_MANUAL_JOURNAL') or hasRole('ADMIN')")
    public ResponseEntity<?> submit(@PathVariable Long id, @AuthenticationPrincipal UserDetails principal) {
        try {
            Long submitterId = actorResolver.requireActingUserId(principal);
            return runTransition(() -> manualJournalService.submit(id, submitterId));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('GL_APPROVE') or hasRole('ADMIN')")
    public ResponseEntity<?> approve(@PathVariable Long id, @AuthenticationPrincipal UserDetails principal) {
        try {
            Long approverId = actorResolver.requireActingUserId(principal);
            return runTransition(() -> manualJournalService.approve(id, approverId));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('GL_APPROVE') or hasRole('ADMIN')")
    public ResponseEntity<?> reject(@PathVariable Long id, @Valid @RequestBody RejectManualJournalRequest request,
                                     @AuthenticationPrincipal UserDetails principal) {
        try {
            Long rejecterId = actorResolver.requireActingUserId(principal);
            return runTransition(() -> manualJournalService.reject(id, rejecterId, request.getReason()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/post")
    @PreAuthorize("hasAuthority('GL_POST') or hasRole('ADMIN')")
    public ResponseEntity<?> post(@PathVariable Long id, @AuthenticationPrincipal UserDetails principal) {
        try {
            Long posterId = actorResolver.requireActingUserId(principal);
            return runTransition(() -> manualJournalService.post(id, posterId));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    private ResponseEntity<?> runTransition(Supplier<ManualJournalResponse> action) {
        try {
            return ResponseEntity.ok(action.get());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException | GLPostingException e) {
            log.warn("Invalid manual journal transition: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }
}
