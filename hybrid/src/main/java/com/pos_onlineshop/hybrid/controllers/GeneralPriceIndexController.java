package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.generalPriceIndex.GeneralPriceIndex;
import com.pos_onlineshop.hybrid.services.GeneralPriceIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/general-price-index")
@RequiredArgsConstructor
@Slf4j
public class GeneralPriceIndexController {

    private final GeneralPriceIndexService generalPriceIndexService;

    @GetMapping
    @PreAuthorize("hasAuthority('GL_VIEW') or hasRole('ADMIN')")
    public List<GeneralPriceIndex> list() {
        return generalPriceIndexService.findAll();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('GL_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<?> record(@RequestBody RecordGeneralPriceIndexRequest request) {
        try {
            GeneralPriceIndex saved = generalPriceIndexService.recordIndexValue(
                    request.indexDate(), request.indexValue(), request.source());
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/conversion-factor")
    @PreAuthorize("hasAuthority('GL_VIEW') or hasRole('ADMIN')")
    public ResponseEntity<?> conversionFactor(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        try {
            BigDecimal factor = generalPriceIndexService.getConversionFactor(from, to);
            return ResponseEntity.ok(Map.of("from", from, "to", to, "factor", factor));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private record RecordGeneralPriceIndexRequest(LocalDate indexDate, BigDecimal indexValue, String source) {
    }
}
