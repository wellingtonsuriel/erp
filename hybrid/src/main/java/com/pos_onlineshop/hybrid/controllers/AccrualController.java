package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.AccrualResponse;
import com.pos_onlineshop.hybrid.dtos.CreateAccrualRequest;
import com.pos_onlineshop.hybrid.gl.GLPostingException;
import com.pos_onlineshop.hybrid.security.AuthenticatedActorResolver;
import com.pos_onlineshop.hybrid.services.AccrualService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * create/reverse's acting-user id must always be the authenticated caller, never
 * CreateAccrualRequest.createdByUserId or reverse's own client-suppliable userId query
 * parameter - see AccrualService's class comment for why (the same request-supplied-identity
 * bug fixed on ManualJournalController), resolved here via the shared
 * {@link AuthenticatedActorResolver}.
 */
@RestController
@RequestMapping("/api/accruals")
@RequiredArgsConstructor
@Slf4j
public class AccrualController {

    private final AccrualService accrualService;
    private final AuthenticatedActorResolver actorResolver;

    @GetMapping
    @PreAuthorize("hasAuthority('GL_VIEW') or hasRole('ADMIN')")
    public List<AccrualResponse> list() {
        return accrualService.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('GL_VIEW') or hasRole('ADMIN')")
    public ResponseEntity<?> get(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(accrualService.findById(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('GL_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<?> create(@Valid @RequestBody CreateAccrualRequest request,
                                     @AuthenticationPrincipal UserDetails principal) {
        try {
            Long creatorId = actorResolver.requireActingUserId(principal);
            return ResponseEntity.status(HttpStatus.CREATED).body(accrualService.createAccrual(request, creatorId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (GLPostingException e) {
            log.warn("Failed to post accrual: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAuthority('GL_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<?> reverse(@PathVariable Long id, @AuthenticationPrincipal UserDetails principal) {
        Long reverserId;
        try {
            reverserId = actorResolver.requireActingUserId(principal);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
        try {
            return ResponseEntity.ok(accrualService.reverseAccrual(id, reverserId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/reverse-due")
    @PreAuthorize("hasAuthority('GL_ADMIN') or hasRole('ADMIN')")
    public List<AccrualResponse> reverseDue(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        return accrualService.reverseDueAccruals(asOfDate);
    }
}
