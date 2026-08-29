package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.FxRevaluationResponse;
import com.pos_onlineshop.hybrid.services.FxRevaluationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/fx-revaluations")
@RequiredArgsConstructor
@Slf4j
public class FxRevaluationController {

    private final FxRevaluationService fxRevaluationService;

    @GetMapping
    @PreAuthorize("hasAuthority('GL_VIEW') or hasRole('ADMIN')")
    public List<FxRevaluationResponse> list() {
        return fxRevaluationService.findAll();
    }

    @PostMapping("/run")
    @PreAuthorize("hasAuthority('GL_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<?> run(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate revaluationDate,
            @RequestBody(required = false) Map<String, String> body) {
        String performedBy = body != null ? body.getOrDefault("performedBy", "system") : "system";
        LocalDate date = revaluationDate != null ? revaluationDate : LocalDate.now();
        return ResponseEntity.ok(fxRevaluationService.revalueOpenBalances(date, performedBy));
    }
}
