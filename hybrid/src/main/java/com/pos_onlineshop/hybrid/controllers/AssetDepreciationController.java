package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.AssetDepreciationResponse;
import com.pos_onlineshop.hybrid.services.AssetDepreciationService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/asset-depreciations")
@RequiredArgsConstructor
public class AssetDepreciationController {

    private final AssetDepreciationService assetDepreciationService;

    @GetMapping
    @PreAuthorize("hasAuthority('GL_VIEW') or hasRole('ADMIN')")
    public List<AssetDepreciationResponse> list() {
        return assetDepreciationService.findAll();
    }

    @PostMapping("/run")
    @PreAuthorize("hasAuthority('GL_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<?> run(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodDate,
            @RequestBody(required = false) Map<String, String> body) {
        String performedBy = body != null ? body.getOrDefault("performedBy", "system") : "system";
        LocalDate date = periodDate != null ? periodDate : LocalDate.now();
        return ResponseEntity.ok(assetDepreciationService.runMonthlyDepreciation(date, performedBy));
    }
}
