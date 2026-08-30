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
 * The acting user for every state transition (and the preparer on create) is always resolved
 * from the JWT-authenticated principal, never trusted from the request body - otherwise anyone
 * holding a valid token could forge the "createdByUserId"/"userId" field to impersonate a
 * different approver, which would silently defeat the maker-checker self-approval check.
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
                                     @AuthenticationPrincipal UserDetails userDetails) {
        try {
            request.setCreatedByUserId(actorResolver.requireActingUserId(userDetails));
            return ResponseEntity.status(HttpStatus.CREATED).body(manualJournalService.create(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        } catch (GLPostingException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('GL_MANUAL_JOURNAL') or hasRole('ADMIN')")
    public ResponseEntity<?> submit(@PathVariable Long id, @Valid @RequestBody ManualJournalActionRequest request,
                                     @AuthenticationPrincipal UserDetails userDetails) {
        return runTransition(userDetails, request::setUserId, () -> manualJournalService.submit(id, request));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('GL_APPROVE') or hasRole('ADMIN')")
    public ResponseEntity<?> approve(@PathVariable Long id, @Valid @RequestBody ManualJournalActionRequest request,
                                      @AuthenticationPrincipal UserDetails userDetails) {
        return runTransition(userDetails, request::setUserId, () -> manualJournalService.approve(id, request));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('GL_APPROVE') or hasRole('ADMIN')")
    public ResponseEntity<?> reject(@PathVariable Long id, @Valid @RequestBody RejectManualJournalRequest request,
                                     @AuthenticationPrincipal UserDetails userDetails) {
        return runTransition(userDetails, request::setUserId, () -> manualJournalService.reject(id, request));
    }

    @PostMapping("/{id}/post")
    @PreAuthorize("hasAuthority('GL_POST') or hasRole('ADMIN')")
    public ResponseEntity<?> post(@PathVariable Long id, @Valid @RequestBody ManualJournalActionRequest request,
                                   @AuthenticationPrincipal UserDetails userDetails) {
        return runTransition(userDetails, request::setUserId, () -> manualJournalService.post(id, request));
    }

    private ResponseEntity<?> runTransition(UserDetails userDetails, java.util.function.Consumer<Long> userIdSetter,
                                             Supplier<ManualJournalResponse> action) {
        try {
            userIdSetter.accept(actorResolver.requireActingUserId(userDetails));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
        return runTransition(action);
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
