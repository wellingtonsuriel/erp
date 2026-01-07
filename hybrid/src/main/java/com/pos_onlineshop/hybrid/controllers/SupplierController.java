package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.*;
import com.pos_onlineshop.hybrid.services.SupplierService;
import com.pos_onlineshop.hybrid.suppliers.Suppliers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class SupplierController {

    private final SupplierService supplierService;

    /**
     * Get all suppliers
     */
    @GetMapping
    public ResponseEntity<List<Suppliers>> getAllSuppliers() {
        List<Suppliers> suppliers = supplierService.findAll();
        return ResponseEntity.ok(suppliers);
    }

    /**
     * Get all suppliers with pagination
     */
    @GetMapping("/paginated")
    public ResponseEntity<Page<Suppliers>> getAllSuppliersPaginated(Pageable pageable) {
        Page<Suppliers> suppliers = supplierService.findAll(pageable);
        return ResponseEntity.ok(suppliers);
    }

    /**
     * Get supplier by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<Suppliers> getSupplierById(@PathVariable Long id) {
        return supplierService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get supplier by code
     */
    @GetMapping("/code/{code}")
    public ResponseEntity<Suppliers> getSupplierByCode(@PathVariable String code) {
        return supplierService.findByCode(code)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get supplier by tax ID
     */
    @GetMapping("/tax-id/{taxId}")
    public ResponseEntity<Suppliers> getSupplierByTaxId(@PathVariable String taxId) {
        return supplierService.findByTaxId(taxId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get all active suppliers
     */
    @GetMapping("/active")
    public ResponseEntity<List<Suppliers>> getActiveSuppliers() {
        List<Suppliers> suppliers = supplierService.findActiveSuppliers();
        return ResponseEntity.ok(suppliers);
    }

    /**
     * Get all verified suppliers
     */
    @GetMapping("/verified")
    public ResponseEntity<List<Suppliers>> getVerifiedSuppliers() {
        List<Suppliers> suppliers = supplierService.findVerifiedSuppliers();
        return ResponseEntity.ok(suppliers);
    }

    /**
     * Get all active and verified suppliers
     */
    @GetMapping("/active-verified")
    public ResponseEntity<List<Suppliers>> getActiveAndVerifiedSuppliers() {
        List<Suppliers> suppliers = supplierService.findActiveAndVerifiedSuppliers();
        return ResponseEntity.ok(suppliers);
    }

    /**
     * Get all active suppliers ordered by name
     */
    @GetMapping("/active/ordered")
    public ResponseEntity<List<Suppliers>> getAllActiveOrdered() {
        List<Suppliers> suppliers = supplierService.findAllActiveOrdered();
        return ResponseEntity.ok(suppliers);
    }

    /**
     * Get all active and verified suppliers ordered by name
     */
    @GetMapping("/active-verified/ordered")
    public ResponseEntity<List<Suppliers>> getAllActiveAndVerifiedOrdered() {
        List<Suppliers> suppliers = supplierService.findAllActiveAndVerifiedOrdered();
        return ResponseEntity.ok(suppliers);
    }

    /**
     * Search suppliers by name or code
     */
    @GetMapping("/search")
    public ResponseEntity<List<Suppliers>> searchSuppliers(@RequestParam String term) {
        List<Suppliers> suppliers = supplierService.searchByNameOrCode(term);
        return ResponseEntity.ok(suppliers);
    }

    /**
     * Get suppliers by country
     */
    @GetMapping("/country/{country}")
    public ResponseEntity<List<Suppliers>> getSuppliersByCountry(@PathVariable String country) {
        List<Suppliers> suppliers = supplierService.findByCountry(country);
        return ResponseEntity.ok(suppliers);
    }

    /**
     * Get suppliers by city
     */
    @GetMapping("/city/{city}")
    public ResponseEntity<List<Suppliers>> getSuppliersByCity(@PathVariable String city) {
        List<Suppliers> suppliers = supplierService.findByCity(city);
        return ResponseEntity.ok(suppliers);
    }

    /**
     * Get suppliers with minimum rating
     */
    @GetMapping("/rating")
    public ResponseEntity<List<Suppliers>> getSuppliersByMinRating(
            @RequestParam(defaultValue = "1") Integer minRating) {
        try {
            List<Suppliers> suppliers = supplierService.findByMinRating(minRating);
            return ResponseEntity.ok(suppliers);
        } catch (IllegalArgumentException e) {
            log.error("Error fetching suppliers by rating", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Create a new supplier
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Suppliers> createSupplier(@Valid @RequestBody CreateSupplierRequest request) {
        try {
            Suppliers supplier = Suppliers.builder()
                    .code(request.getCode())
                    .name(request.getName())
                    .contactPerson(request.getContactPerson())
                    .email(request.getEmail())
                    .phoneNumber(request.getPhoneNumber())
                    .mobileNumber(request.getMobileNumber())
                    .faxNumber(request.getFaxNumber())
                    .address(request.getAddress())
                    .city(request.getCity())
                    .state(request.getState())
                    .country(request.getCountry())
                    .postalCode(request.getPostalCode())
                    .taxId(request.getTaxId())
                    .registrationNumber(request.getRegistrationNumber())
                    .paymentTerms(request.getPaymentTerms())
                    .creditLimit(request.getCreditLimit())
                    .website(request.getWebsite())
                    .notes(request.getNotes())
                    .active(request.isActive())
                    .verified(request.isVerified())
                    .rating(request.getRating())
                    .leadTimeDays(request.getLeadTimeDays())
                    .minimumOrderAmount(request.getMinimumOrderAmount())
                    .build();

            Suppliers created = supplierService.createSupplier(supplier);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            log.error("Validation error creating supplier", e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error creating supplier", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Update an existing supplier
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Suppliers> updateSupplier(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSupplierRequest request) {
        try {
            Suppliers supplierDetails = Suppliers.builder()
                    .code(request.getCode())
                    .name(request.getName())
                    .contactPerson(request.getContactPerson())
                    .email(request.getEmail())
                    .phoneNumber(request.getPhoneNumber())
                    .mobileNumber(request.getMobileNumber())
                    .faxNumber(request.getFaxNumber())
                    .address(request.getAddress())
                    .city(request.getCity())
                    .state(request.getState())
                    .country(request.getCountry())
                    .postalCode(request.getPostalCode())
                    .taxId(request.getTaxId())
                    .registrationNumber(request.getRegistrationNumber())
                    .paymentTerms(request.getPaymentTerms())
                    .creditLimit(request.getCreditLimit())
                    .website(request.getWebsite())
                    .notes(request.getNotes())
                    .rating(request.getRating())
                    .leadTimeDays(request.getLeadTimeDays())
                    .minimumOrderAmount(request.getMinimumOrderAmount())
                    .build();

            Suppliers updated = supplierService.updateSupplier(id, supplierDetails);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            log.error("Validation error updating supplier", e);
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException e) {
            log.error("Error updating supplier", e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Delete a supplier
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteSupplier(@PathVariable Long id) {
        try {
            supplierService.deleteSupplier(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            log.error("Error deleting supplier", e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Activate a supplier
     */
    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> activateSupplier(@PathVariable Long id) {
        try {
            supplierService.activateSupplier(id);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            log.error("Error activating supplier", e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Deactivate a supplier
     */
    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivateSupplier(@PathVariable Long id) {
        try {
            supplierService.deactivateSupplier(id);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            log.error("Error deactivating supplier", e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Verify a supplier
     */
    @PostMapping("/{id}/verify")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> verifySupplier(@PathVariable Long id) {
        try {
            supplierService.verifySupplier(id);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            log.error("Error verifying supplier", e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Unverify a supplier
     */
    @PostMapping("/{id}/unverify")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> unverifySupplier(@PathVariable Long id) {
        try {
            supplierService.unverifySupplier(id);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            log.error("Error unverifying supplier", e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Update supplier rating
     */
    @PutMapping("/{id}/rating")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Suppliers> updateSupplierRating(
            @PathVariable Long id,
            @RequestBody SupplierRatingRequest request) {
        try {
            Suppliers updated = supplierService.updateRating(id, request.getRating());
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            log.error("Validation error updating supplier rating", e);
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException e) {
            log.error("Error updating supplier rating", e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Bulk activate suppliers
     */
    @PostMapping("/bulk-activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> bulkActivateSuppliers(@RequestBody BulkSupplierActionRequest request) {
        try {
            supplierService.bulkActivateSuppliers(request.getSupplierIds());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error bulk activating suppliers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Bulk deactivate suppliers
     */
    @PostMapping("/bulk-deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> bulkDeactivateSuppliers(@RequestBody BulkSupplierActionRequest request) {
        try {
            supplierService.bulkDeactivateSuppliers(request.getSupplierIds());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error bulk deactivating suppliers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Check if supplier exists by code
     */
    @GetMapping("/exists/code/{code}")
    public ResponseEntity<Boolean> existsByCode(@PathVariable String code) {
        boolean exists = supplierService.existsByCode(code);
        return ResponseEntity.ok(exists);
    }

    /**
     * Check if supplier exists by tax ID
     */
    @GetMapping("/exists/tax-id/{taxId}")
    public ResponseEntity<Boolean> existsByTaxId(@PathVariable String taxId) {
        boolean exists = supplierService.existsByTaxId(taxId);
        return ResponseEntity.ok(exists);
    }
}
