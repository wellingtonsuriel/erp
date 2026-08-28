package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.JournalEntryResponse;
import com.pos_onlineshop.hybrid.dtos.ReverseJournalEntryRequest;
import com.pos_onlineshop.hybrid.enums.GLSourceModule;
import com.pos_onlineshop.hybrid.enums.JournalStatus;
import com.pos_onlineshop.hybrid.gl.GLPostingException;
import com.pos_onlineshop.hybrid.services.JournalEntryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/journal-entries")
@RequiredArgsConstructor
@Slf4j
public class JournalEntryController {

    private final JournalEntryService journalEntryService;

    @GetMapping
    @PreAuthorize("hasAuthority('GL_VIEW') or hasRole('ADMIN')")
    public List<JournalEntryResponse> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long periodId,
            @RequestParam(required = false) GLSourceModule sourceModule,
            @RequestParam(required = false) String sourceReferenceType,
            @RequestParam(required = false) Long sourceReferenceId,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) Long shopId,
            @RequestParam(required = false) JournalStatus status,
            @RequestParam(required = false) Long entryNumber) {
        return journalEntryService.search(fromDate, toDate, periodId, sourceModule, sourceReferenceType,
                sourceReferenceId, accountId, shopId, status, entryNumber);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('GL_VIEW') or hasRole('ADMIN')")
    public ResponseEntity<?> get(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(journalEntryService.findById(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAuthority('GL_REVERSE') or hasRole('ADMIN')")
    public ResponseEntity<?> reverse(@PathVariable Long id, @Valid @RequestBody ReverseJournalEntryRequest request) {
        try {
            return ResponseEntity.ok(journalEntryService.reverse(id, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (GLPostingException e) {
            log.warn("Cannot reverse journal entry {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }
}
