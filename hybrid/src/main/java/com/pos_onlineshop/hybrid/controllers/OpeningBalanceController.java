package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.CreateOpeningBalanceRequest;
import com.pos_onlineshop.hybrid.dtos.OpeningBalanceResponse;
import com.pos_onlineshop.hybrid.gl.GLPostingException;
import com.pos_onlineshop.hybrid.security.AuthenticatedActorResolver;
import com.pos_onlineshop.hybrid.services.OpeningBalanceService;
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
 * create's acting-user id must always be the authenticated caller, never
 * CreateOpeningBalanceRequest.createdByUserId from the request body - see
 * OpeningBalanceService's class comment for why (the same request-supplied-identity bug
 * fixed on ManualJournalController), resolved here via the shared
 * {@link AuthenticatedActorResolver}.
 */
@RestController
@RequestMapping("/api/opening-balances")
@RequiredArgsConstructor
@Slf4j
public class OpeningBalanceController {

    private final OpeningBalanceService openingBalanceService;
    private final AuthenticatedActorResolver actorResolver;

    @GetMapping
    @PreAuthorize("hasAuthority('GL_VIEW') or hasRole('ADMIN')")
    public List<OpeningBalanceResponse> list() {
        return openingBalanceService.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('GL_VIEW') or hasRole('ADMIN')")
    public ResponseEntity<?> get(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(openingBalanceService.findById(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('GL_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<?> create(@Valid @RequestBody CreateOpeningBalanceRequest request,
                                     @AuthenticationPrincipal UserDetails principal) {
        try {
            Long creatorId = actorResolver.requireActingUserId(principal);
            return ResponseEntity.status(HttpStatus.CREATED).body(openingBalanceService.createOpeningBalance(request, creatorId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (GLPostingException e) {
            log.warn("Failed to post opening balance: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
