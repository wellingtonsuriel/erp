package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.customers.Customers;
import com.pos_onlineshop.hybrid.services.CustomersService;
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
import java.util.Map;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class CustomersController {

    private final CustomersService customersService;

    /**
     * Get all customers
     */
    @GetMapping
    public ResponseEntity<List<Customers>> getAllCustomers() {
        List<Customers> customers = customersService.findAll();
        return ResponseEntity.ok(customers);
    }

    /**
     * Get all customers with pagination
     */
    @GetMapping("/paginated")
    public ResponseEntity<Page<Customers>> getAllCustomersPaginated(Pageable pageable) {
        Page<Customers> customers = customersService.findAll(pageable);
        return ResponseEntity.ok(customers);
    }

    /**
     * Get customer by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<Customers> getCustomerById(@PathVariable Long id) {
        return customersService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get customer by code
     */
    @GetMapping("/code/{code}")
    public ResponseEntity<Customers> getCustomerByCode(@PathVariable String code) {
        return customersService.findByCode(code)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get customer by email
     */
    @GetMapping("/email/{email}")
    public ResponseEntity<Customers> getCustomerByEmail(@PathVariable String email) {
        return customersService.findByEmail(email)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get customer by tax ID
     */
    @GetMapping("/tax-id/{taxId}")
    public ResponseEntity<Customers> getCustomerByTaxId(@PathVariable String taxId) {
        return customersService.findByTaxId(taxId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get all active customers
     */
    @GetMapping("/active")
    public ResponseEntity<List<Customers>> getActiveCustomers() {
        List<Customers> customers = customersService.findActiveCustomers();
        return ResponseEntity.ok(customers);
    }

    /**
     * Get all verified customers
     */
    @GetMapping("/verified")
    public ResponseEntity<List<Customers>> getVerifiedCustomers() {
        List<Customers> customers = customersService.findVerifiedCustomers();
        return ResponseEntity.ok(customers);
    }

    /**
     * Get all active and verified customers
     */
    @GetMapping("/active-verified")
    public ResponseEntity<List<Customers>> getActiveAndVerifiedCustomers() {
        List<Customers> customers = customersService.findActiveAndVerifiedCustomers();
        return ResponseEntity.ok(customers);
    }

    /**
     * Get all active customers ordered by name
     */
    @GetMapping("/active/ordered")
    public ResponseEntity<List<Customers>> getAllActiveOrdered() {
        List<Customers> customers = customersService.findAllActiveOrdered();
        return ResponseEntity.ok(customers);
    }

    /**
     * Get all active and verified customers ordered by name
     */
    @GetMapping("/active-verified/ordered")
    public ResponseEntity<List<Customers>> getAllActiveAndVerifiedOrdered() {
        List<Customers> customers = customersService.findAllActiveAndVerifiedOrdered();
        return ResponseEntity.ok(customers);
    }

    /**
     * Search customers by name, code, or email
     */
    @GetMapping("/search")
    public ResponseEntity<List<Customers>> searchCustomers(@RequestParam String term) {
        List<Customers> customers = customersService.searchByNameOrCodeOrEmail(term);
        return ResponseEntity.ok(customers);
    }

    /**
     * Get customers by country
     */
    @GetMapping("/country/{country}")
    public ResponseEntity<List<Customers>> getCustomersByCountry(@PathVariable String country) {
        List<Customers> customers = customersService.findByCountry(country);
        return ResponseEntity.ok(customers);
    }

    /**
     * Get customers by city
     */
    @GetMapping("/city/{city}")
    public ResponseEntity<List<Customers>> getCustomersByCity(@PathVariable String city) {
        List<Customers> customers = customersService.findByCity(city);
        return ResponseEntity.ok(customers);
    }

    /**
     * Get customers with minimum loyalty points
     */
    @GetMapping("/loyalty-points")
    public ResponseEntity<List<Customers>> getCustomersByMinLoyaltyPoints(
            @RequestParam(defaultValue = "0") Integer minPoints) {
        try {
            List<Customers> customers = customersService.findByMinLoyaltyPoints(minPoints);
            return ResponseEntity.ok(customers);
        } catch (IllegalArgumentException e) {
            log.error("Error fetching customers by loyalty points", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Create a new customer
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('CASHIER')")
    public ResponseEntity<Customers> createCustomer(@Valid @RequestBody Customers customer) {
        try {
            Customers created = customersService.createCustomer(customer);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            log.error("Validation error creating customer", e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error creating customer", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Update an existing customer
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CASHIER')")
    public ResponseEntity<Customers> updateCustomer(
            @PathVariable Long id,
            @Valid @RequestBody Customers customerDetails) {
        try {
            Customers updated = customersService.updateCustomer(id, customerDetails);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            log.error("Validation error updating customer", e);
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException e) {
            log.error("Error updating customer", e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Delete a customer
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteCustomer(@PathVariable Long id) {
        try {
            customersService.deleteCustomer(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            log.error("Error deleting customer", e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Activate a customer
     */
    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> activateCustomer(@PathVariable Long id) {
        try {
            customersService.activateCustomer(id);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            log.error("Error activating customer", e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Deactivate a customer
     */
    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivateCustomer(@PathVariable Long id) {
        try {
            customersService.deactivateCustomer(id);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            log.error("Error deactivating customer", e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Verify a customer
     */
    @PostMapping("/{id}/verify")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> verifyCustomer(@PathVariable Long id) {
        try {
            customersService.verifyCustomer(id);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            log.error("Error verifying customer", e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Unverify a customer
     */
    @PostMapping("/{id}/unverify")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> unverifyCustomer(@PathVariable Long id) {
        try {
            customersService.unverifyCustomer(id);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            log.error("Error unverifying customer", e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Add loyalty points to customer
     */
    @PostMapping("/{id}/loyalty-points/add")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CASHIER')")
    public ResponseEntity<Customers> addLoyaltyPoints(
            @PathVariable Long id,
            @RequestBody Map<String, Integer> request) {
        try {
            Integer points = request.get("points");
            if (points == null) {
                return ResponseEntity.badRequest().build();
            }
            Customers updated = customersService.addLoyaltyPoints(id, points);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            log.error("Validation error adding loyalty points", e);
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException e) {
            log.error("Error adding loyalty points", e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Redeem loyalty points from customer
     */
    @PostMapping("/{id}/loyalty-points/redeem")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CASHIER')")
    public ResponseEntity<Customers> redeemLoyaltyPoints(
            @PathVariable Long id,
            @RequestBody Map<String, Integer> request) {
        try {
            Integer points = request.get("points");
            if (points == null) {
                return ResponseEntity.badRequest().build();
            }
            Customers updated = customersService.redeemLoyaltyPoints(id, points);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            log.error("Validation error redeeming loyalty points", e);
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException e) {
            log.error("Error redeeming loyalty points", e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Bulk activate customers
     */
    @PostMapping("/bulk-activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> bulkActivateCustomers(@RequestBody Map<String, List<Long>> request) {
        try {
            List<Long> customerIds = request.get("customerIds");
            if (customerIds == null || customerIds.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            customersService.bulkActivateCustomers(customerIds);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error bulk activating customers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Bulk deactivate customers
     */
    @PostMapping("/bulk-deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> bulkDeactivateCustomers(@RequestBody Map<String, List<Long>> request) {
        try {
            List<Long> customerIds = request.get("customerIds");
            if (customerIds == null || customerIds.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            customersService.bulkDeactivateCustomers(customerIds);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error bulk deactivating customers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Check if customer exists by code
     */
    @GetMapping("/exists/code/{code}")
    public ResponseEntity<Boolean> existsByCode(@PathVariable String code) {
        boolean exists = customersService.existsByCode(code);
        return ResponseEntity.ok(exists);
    }

    /**
     * Check if customer exists by tax ID
     */
    @GetMapping("/exists/tax-id/{taxId}")
    public ResponseEntity<Boolean> existsByTaxId(@PathVariable String taxId) {
        boolean exists = customersService.existsByTaxId(taxId);
        return ResponseEntity.ok(exists);
    }

    /**
     * Check if customer exists by email
     */
    @GetMapping("/exists/email/{email}")
    public ResponseEntity<Boolean> existsByEmail(@PathVariable String email) {
        boolean exists = customersService.existsByEmail(email);
        return ResponseEntity.ok(exists);
    }
}
