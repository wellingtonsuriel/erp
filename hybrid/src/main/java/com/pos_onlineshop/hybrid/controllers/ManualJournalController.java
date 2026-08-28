package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.*;
import com.pos_onlineshop.hybrid.gl.GLPostingException;
import com.pos_onlineshop.hybrid.services.ManualJournalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/manual-journals")
@RequiredArgsConstructor
@Slf4j
public class ManualJournalController {

    private final ManualJournalService manualJournalService;

    @GetMapping
    public List<ManualJournalResponse> list() {
        return manualJournalService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(manualJournalService.findById(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CreateManualJournalRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(manualJournalService.create(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (GLPostingException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<?> submit(@PathVariable Long id, @Valid @RequestBody ManualJournalActionRequest request) {
        return runTransition(() -> manualJournalService.submit(id, request));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable Long id, @Valid @RequestBody ManualJournalActionRequest request) {
        return runTransition(() -> manualJournalService.approve(id, request));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable Long id, @Valid @RequestBody RejectManualJournalRequest request) {
        return runTransition(() -> manualJournalService.reject(id, request));
    }

    @PostMapping("/{id}/post")
    public ResponseEntity<?> post(@PathVariable Long id, @Valid @RequestBody ManualJournalActionRequest request) {
        return runTransition(() -> manualJournalService.post(id, request));
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
