package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.Ias29RestatementResponse;
import com.pos_onlineshop.hybrid.gl.GLPostingException;
import com.pos_onlineshop.hybrid.services.Ias29RestatementService;
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
@RequestMapping("/api/ias29-restatements")
@RequiredArgsConstructor
@Slf4j
public class Ias29RestatementController {

    private final Ias29RestatementService ias29RestatementService;

    @GetMapping
    @PreAuthorize("hasAuthority('GL_VIEW') or hasRole('ADMIN')")
    public List<Ias29RestatementResponse> list() {
        return ias29RestatementService.findAll();
    }

    @PostMapping("/run")
    @PreAuthorize("hasAuthority('GL_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<?> run(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate restatementDate,
            @RequestBody(required = false) Map<String, String> body) {
        String performedBy = body != null ? body.getOrDefault("performedBy", "system") : "system";
        LocalDate date = restatementDate != null ? restatementDate : LocalDate.now();
        try {
            return ResponseEntity.ok(ias29RestatementService.restateFixedAssets(date, performedBy));
        } catch (IllegalStateException | GLPostingException e) {
            log.warn("IAS 29 restatement run failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAuthority('GL_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<?> reverse(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String reason = body.getOrDefault("reason", "Correction");
        String performedBy = body.getOrDefault("performedBy", "system");
        try {
            return ResponseEntity.ok(ias29RestatementService.reverseRestatement(id, reason, performedBy));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException | GLPostingException e) {
            log.warn("IAS 29 restatement reversal failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }
}
