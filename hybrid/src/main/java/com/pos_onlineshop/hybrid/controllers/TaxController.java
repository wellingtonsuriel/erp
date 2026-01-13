package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.CreateTaxRequest;
import com.pos_onlineshop.hybrid.dtos.TaxResponse;
import com.pos_onlineshop.hybrid.dtos.UpdateTaxRequest;
import com.pos_onlineshop.hybrid.enums.TaxCalculationType;
import com.pos_onlineshop.hybrid.enums.TaxNature;
import com.pos_onlineshop.hybrid.services.TaxService;
import com.pos_onlineshop.hybrid.tax.Tax;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for managing taxes.
 * Provides endpoints for CRUD operations and tax queries.
 */
@RestController
@RequestMapping("/api/taxes")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class TaxController {

    private final TaxService taxService;

    /**
     * Create a new tax.
     *
     * @param request the CreateTaxRequest
     * @return the created TaxResponse
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TaxResponse> createTax(@Valid @RequestBody CreateTaxRequest request) {
        log.info("POST /api/taxes - Creating new tax: {}", request.getTaxName());
        try {
            TaxResponse response = taxService.createTaxFromRequest(request);
            return new ResponseEntity<>(response, HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            log.error("Error creating tax: {}", e.getMessage());
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            log.error("Unexpected error creating tax", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Update an existing tax.
     *
     * @param taxId   the tax ID
     * @param request the UpdateTaxRequest
     * @return the updated TaxResponse
     */
    @PutMapping("/{taxId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TaxResponse> updateTax(
            @PathVariable Long taxId,
            @Valid @RequestBody UpdateTaxRequest request) {
        log.info("PUT /api/taxes/{} - Updating tax", taxId);
        try {
            TaxResponse response = taxService.updateTaxFromRequest(taxId, request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Error updating tax: {}", e.getMessage());
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            log.error("Unexpected error updating tax", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Delete a tax by ID.
     *
     * @param taxId the tax ID
     * @return no content response
     */
    @DeleteMapping("/{taxId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteTax(@PathVariable Long taxId) {
        log.info("DELETE /api/taxes/{} - Deleting tax", taxId);
        try {
            taxService.deleteTax(taxId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            log.error("Error deleting tax: {}", e.getMessage());
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            log.error("Unexpected error deleting tax", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Get a tax by ID.
     *
     * @param taxId the tax ID
     * @return the TaxResponse
     */
    @GetMapping("/{taxId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CASHIER')")
    public ResponseEntity<TaxResponse> getTaxById(@PathVariable Long taxId) {
        log.info("GET /api/taxes/{} - Getting tax by ID", taxId);
        try {
            TaxResponse response = taxService.findByIdAsResponse(taxId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Tax not found: {}", e.getMessage());
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            log.error("Unexpected error getting tax", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Get all taxes.
     *
     * @return list of all taxes
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('CASHIER')")
    public ResponseEntity<List<TaxResponse>> getAllTaxes() {
        log.info("GET /api/taxes - Getting all taxes");
        try {
            List<TaxResponse> taxes = taxService.findAllAsResponses();
            return ResponseEntity.ok(taxes);
        } catch (Exception e) {
            log.error("Error getting all taxes", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Get all taxes with pagination.
     *
     * @param pageable pagination information
     * @return page of taxes
     */
    @GetMapping("/paginated")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CASHIER')")
    public ResponseEntity<Page<TaxResponse>> getAllTaxesPaginated(Pageable pageable) {
        log.info("GET /api/taxes/paginated - Getting all taxes with pagination");
        try {
            Page<TaxResponse> taxes = taxService.findAllAsResponses(pageable);
            return ResponseEntity.ok(taxes);
        } catch (Exception e) {
            log.error("Error getting paginated taxes", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Get all active taxes.
     *
     * @return list of active taxes
     */
    @GetMapping("/active")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CASHIER')")
    public ResponseEntity<List<TaxResponse>> getActiveTaxes() {
        log.info("GET /api/taxes/active - Getting all active taxes");
        try {
            List<TaxResponse> taxes = taxService.findAllActiveAsResponses();
            return ResponseEntity.ok(taxes);
        } catch (Exception e) {
            log.error("Error getting active taxes", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Get taxes by nature.
     *
     * @param taxNature the tax nature
     * @return list of taxes with the specified nature
     */
    @GetMapping("/nature/{taxNature}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CASHIER')")
    public ResponseEntity<List<Tax>> getTaxesByNature(@PathVariable TaxNature taxNature) {
        log.info("GET /api/taxes/nature/{} - Getting taxes by nature", taxNature);
        try {
            List<Tax> taxes = taxService.findByTaxNature(taxNature);
            return ResponseEntity.ok(taxes);
        } catch (Exception e) {
            log.error("Error getting taxes by nature", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Get active taxes by nature.
     *
     * @param taxNature the tax nature
     * @return list of active taxes with the specified nature
     */
    @GetMapping("/nature/{taxNature}/active")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CASHIER')")
    public ResponseEntity<List<Tax>> getActiveTaxesByNature(@PathVariable TaxNature taxNature) {
        log.info("GET /api/taxes/nature/{}/active - Getting active taxes by nature", taxNature);
        try {
            List<Tax> taxes = taxService.findActiveTaxesByNature(taxNature);
            return ResponseEntity.ok(taxes);
        } catch (Exception e) {
            log.error("Error getting active taxes by nature", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Get taxes by calculation type.
     *
     * @param calculationType the tax calculation type
     * @return list of taxes with the specified calculation type
     */
    @GetMapping("/calculation-type/{calculationType}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CASHIER')")
    public ResponseEntity<List<Tax>> getTaxesByCalculationType(@PathVariable TaxCalculationType calculationType) {
        log.info("GET /api/taxes/calculation-type/{} - Getting taxes by calculation type", calculationType);
        try {
            List<Tax> taxes = taxService.findByCalculationType(calculationType);
            return ResponseEntity.ok(taxes);
        } catch (Exception e) {
            log.error("Error getting taxes by calculation type", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Get active taxes by calculation type.
     *
     * @param calculationType the tax calculation type
     * @return list of active taxes with the specified calculation type
     */
    @GetMapping("/calculation-type/{calculationType}/active")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CASHIER')")
    public ResponseEntity<List<Tax>> getActiveTaxesByCalculationType(@PathVariable TaxCalculationType calculationType) {
        log.info("GET /api/taxes/calculation-type/{}/active - Getting active taxes by calculation type", calculationType);
        try {
            List<Tax> taxes = taxService.findActiveByCalculationType(calculationType);
            return ResponseEntity.ok(taxes);
        } catch (Exception e) {
            log.error("Error getting active taxes by calculation type", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Search taxes by name.
     *
     * @param name the search term
     * @return list of taxes matching the search term
     */
    @GetMapping("/search")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CASHIER')")
    public ResponseEntity<List<Tax>> searchTaxesByName(@RequestParam String name) {
        log.info("GET /api/taxes/search?name={} - Searching taxes by name", name);
        try {
            List<Tax> taxes = taxService.searchByName(name);
            return ResponseEntity.ok(taxes);
        } catch (Exception e) {
            log.error("Error searching taxes by name", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
