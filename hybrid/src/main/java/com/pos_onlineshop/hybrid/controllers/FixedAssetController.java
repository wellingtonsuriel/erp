package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.CreateFixedAssetCategoryRequest;
import com.pos_onlineshop.hybrid.dtos.CreateFixedAssetRequest;
import com.pos_onlineshop.hybrid.dtos.DisposeAssetRequest;
import com.pos_onlineshop.hybrid.dtos.FixedAssetResponse;
import com.pos_onlineshop.hybrid.fixedAssetCategory.FixedAssetCategory;
import com.pos_onlineshop.hybrid.services.AssetDisposalService;
import com.pos_onlineshop.hybrid.services.FixedAssetCategoryService;
import com.pos_onlineshop.hybrid.services.FixedAssetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/fixed-assets")
@RequiredArgsConstructor
@Slf4j
public class FixedAssetController {

    private final FixedAssetService fixedAssetService;
    private final FixedAssetCategoryService fixedAssetCategoryService;
    private final AssetDisposalService assetDisposalService;

    @GetMapping
    @PreAuthorize("hasAuthority('GL_VIEW') or hasRole('ADMIN')")
    public List<FixedAssetResponse> list() {
        return fixedAssetService.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('GL_VIEW') or hasRole('ADMIN')")
    public ResponseEntity<?> get(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(fixedAssetService.findById(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('GL_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<?> register(@Valid @RequestBody CreateFixedAssetRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(fixedAssetService.registerAsset(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/dispose")
    @PreAuthorize("hasAuthority('GL_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<?> dispose(@PathVariable Long id, @Valid @RequestBody DisposeAssetRequest request,
                                      @RequestParam(defaultValue = "system") String performedBy) {
        try {
            return ResponseEntity.ok(assetDisposalService.disposeAsset(id, request, performedBy));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            log.warn("Cannot dispose asset {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/categories")
    @PreAuthorize("hasAuthority('GL_VIEW') or hasRole('ADMIN')")
    public List<FixedAssetCategory> listCategories() {
        return fixedAssetCategoryService.findAllActive();
    }

    @PostMapping("/categories")
    @PreAuthorize("hasAuthority('GL_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<?> createCategory(@Valid @RequestBody CreateFixedAssetCategoryRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(fixedAssetCategoryService.create(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
