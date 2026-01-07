package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.suppliers.Suppliers;
import com.pos_onlineshop.hybrid.suppliers.SuppliersRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SupplierService {

    private final SuppliersRepository suppliersRepository;

    /**
     * Create a new supplier
     */
    public Suppliers createSupplier(Suppliers supplier) {
        log.info("Creating new supplier: {}", supplier.getName());

        // Validate required fields
        validateSupplier(supplier);

        // Check for duplicate code
        if (supplier.getCode() != null && suppliersRepository.existsByCode(supplier.getCode())) {
            throw new IllegalArgumentException("Supplier with code " + supplier.getCode() + " already exists");
        }

        // Check for duplicate tax ID
        if (supplier.getTaxId() != null && suppliersRepository.existsByTaxId(supplier.getTaxId())) {
            throw new IllegalArgumentException("Supplier with tax ID " + supplier.getTaxId() + " already exists");
        }

        Suppliers savedSupplier = suppliersRepository.save(supplier);
        log.info("Successfully created supplier: {} with ID: {}", savedSupplier.getName(), savedSupplier.getId());

        return savedSupplier;
    }

    /**
     * Update an existing supplier
     */
    public Suppliers updateSupplier(Long id, Suppliers supplierDetails) {
        return suppliersRepository.findById(id)
                .map(supplier -> {
                    // Update basic fields
                    if (supplierDetails.getName() != null) {
                        supplier.setName(supplierDetails.getName());
                    }
                    if (supplierDetails.getCode() != null && !supplierDetails.getCode().equals(supplier.getCode())) {
                        if (suppliersRepository.existsByCode(supplierDetails.getCode())) {
                            throw new IllegalArgumentException("Supplier with code " + supplierDetails.getCode() + " already exists");
                        }
                        supplier.setCode(supplierDetails.getCode());
                    }
                    if (supplierDetails.getContactPerson() != null) {
                        supplier.setContactPerson(supplierDetails.getContactPerson());
                    }
                    if (supplierDetails.getEmail() != null) {
                        supplier.setEmail(supplierDetails.getEmail());
                    }
                    if (supplierDetails.getPhoneNumber() != null) {
                        supplier.setPhoneNumber(supplierDetails.getPhoneNumber());
                    }
                    if (supplierDetails.getMobileNumber() != null) {
                        supplier.setMobileNumber(supplierDetails.getMobileNumber());
                    }
                    if (supplierDetails.getFaxNumber() != null) {
                        supplier.setFaxNumber(supplierDetails.getFaxNumber());
                    }
                    if (supplierDetails.getAddress() != null) {
                        supplier.setAddress(supplierDetails.getAddress());
                    }
                    if (supplierDetails.getCity() != null) {
                        supplier.setCity(supplierDetails.getCity());
                    }
                    if (supplierDetails.getState() != null) {
                        supplier.setState(supplierDetails.getState());
                    }
                    if (supplierDetails.getCountry() != null) {
                        supplier.setCountry(supplierDetails.getCountry());
                    }
                    if (supplierDetails.getPostalCode() != null) {
                        supplier.setPostalCode(supplierDetails.getPostalCode());
                    }
                    if (supplierDetails.getTaxId() != null && !supplierDetails.getTaxId().equals(supplier.getTaxId())) {
                        if (suppliersRepository.existsByTaxId(supplierDetails.getTaxId())) {
                            throw new IllegalArgumentException("Supplier with tax ID " + supplierDetails.getTaxId() + " already exists");
                        }
                        supplier.setTaxId(supplierDetails.getTaxId());
                    }
                    if (supplierDetails.getRegistrationNumber() != null) {
                        supplier.setRegistrationNumber(supplierDetails.getRegistrationNumber());
                    }
                    if (supplierDetails.getPaymentTerms() != null) {
                        supplier.setPaymentTerms(supplierDetails.getPaymentTerms());
                    }
                    if (supplierDetails.getCreditLimit() != null) {
                        supplier.setCreditLimit(supplierDetails.getCreditLimit());
                    }
                    if (supplierDetails.getWebsite() != null) {
                        supplier.setWebsite(supplierDetails.getWebsite());
                    }
                    if (supplierDetails.getNotes() != null) {
                        supplier.setNotes(supplierDetails.getNotes());
                    }
                    if (supplierDetails.getRating() != null) {
                        supplier.setRating(supplierDetails.getRating());
                    }
                    if (supplierDetails.getLeadTimeDays() != null) {
                        supplier.setLeadTimeDays(supplierDetails.getLeadTimeDays());
                    }
                    if (supplierDetails.getMinimumOrderAmount() != null) {
                        supplier.setMinimumOrderAmount(supplierDetails.getMinimumOrderAmount());
                    }

                    Suppliers updated = suppliersRepository.save(supplier);
                    log.info("Updated supplier: {}", updated.getName());
                    return updated;
                })
                .orElseThrow(() -> new RuntimeException("Supplier not found: " + id));
    }

    /**
     * Delete a supplier
     */
    public void deleteSupplier(Long id) {
        Optional<Suppliers> supplierOpt = suppliersRepository.findById(id);
        if (supplierOpt.isPresent()) {
            Suppliers supplier = supplierOpt.get();
            suppliersRepository.deleteById(id);
            log.info("Deleted supplier: {} with ID: {}", supplier.getName(), id);
        } else {
            throw new RuntimeException("Supplier not found: " + id);
        }
    }

    /**
     * Soft delete - deactivate supplier
     */
    public void deactivateSupplier(Long id) {
        suppliersRepository.findById(id)
                .ifPresentOrElse(
                        supplier -> {
                            supplier.setActive(false);
                            suppliersRepository.save(supplier);
                            log.info("Deactivated supplier: {}", supplier.getName());
                        },
                        () -> {
                            throw new RuntimeException("Supplier not found: " + id);
                        }
                );
    }

    /**
     * Activate supplier
     */
    public void activateSupplier(Long id) {
        suppliersRepository.findById(id)
                .ifPresentOrElse(
                        supplier -> {
                            supplier.setActive(true);
                            suppliersRepository.save(supplier);
                            log.info("Activated supplier: {}", supplier.getName());
                        },
                        () -> {
                            throw new RuntimeException("Supplier not found: " + id);
                        }
                );
    }

    /**
     * Verify supplier
     */
    public void verifySupplier(Long id) {
        suppliersRepository.findById(id)
                .ifPresentOrElse(
                        supplier -> {
                            supplier.setVerified(true);
                            suppliersRepository.save(supplier);
                            log.info("Verified supplier: {}", supplier.getName());
                        },
                        () -> {
                            throw new RuntimeException("Supplier not found: " + id);
                        }
                );
    }

    /**
     * Unverify supplier
     */
    public void unverifySupplier(Long id) {
        suppliersRepository.findById(id)
                .ifPresentOrElse(
                        supplier -> {
                            supplier.setVerified(false);
                            suppliersRepository.save(supplier);
                            log.info("Unverified supplier: {}", supplier.getName());
                        },
                        () -> {
                            throw new RuntimeException("Supplier not found: " + id);
                        }
                );
    }

    /**
     * Update supplier rating
     */
    public Suppliers updateRating(Long id, Integer rating) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }

        return suppliersRepository.findById(id)
                .map(supplier -> {
                    supplier.setRating(rating);
                    Suppliers updated = suppliersRepository.save(supplier);
                    log.info("Updated rating for supplier {} to {}", supplier.getName(), rating);
                    return updated;
                })
                .orElseThrow(() -> new RuntimeException("Supplier not found: " + id));
    }

    // CRUD Read Operations

    @Transactional(readOnly = true)
    public Optional<Suppliers> findById(Long id) {
        return suppliersRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Suppliers> findByCode(String code) {
        return suppliersRepository.findByCode(code);
    }

    @Transactional(readOnly = true)
    public Optional<Suppliers> findByTaxId(String taxId) {
        return suppliersRepository.findByTaxId(taxId);
    }

    @Transactional(readOnly = true)
    public List<Suppliers> findAll() {
        return suppliersRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Page<Suppliers> findAll(Pageable pageable) {
        return suppliersRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<Suppliers> findActiveSuppliers() {
        return suppliersRepository.findByActiveTrue();
    }

    @Transactional(readOnly = true)
    public List<Suppliers> findVerifiedSuppliers() {
        return suppliersRepository.findByVerifiedTrue();
    }

    @Transactional(readOnly = true)
    public List<Suppliers> findActiveAndVerifiedSuppliers() {
        return suppliersRepository.findByActiveTrueAndVerifiedTrue();
    }

    @Transactional(readOnly = true)
    public List<Suppliers> findAllActiveOrdered() {
        return suppliersRepository.findAllActiveOrdered();
    }

    @Transactional(readOnly = true)
    public List<Suppliers> findAllActiveAndVerifiedOrdered() {
        return suppliersRepository.findAllActiveAndVerifiedOrdered();
    }

    @Transactional(readOnly = true)
    public List<Suppliers> searchByNameOrCode(String searchTerm) {
        return suppliersRepository.searchByNameOrCode(searchTerm);
    }

    @Transactional(readOnly = true)
    public List<Suppliers> findByCountry(String country) {
        return suppliersRepository.findByCountry(country);
    }

    @Transactional(readOnly = true)
    public List<Suppliers> findByCity(String city) {
        return suppliersRepository.findByCity(city);
    }

    @Transactional(readOnly = true)
    public List<Suppliers> findByMinRating(Integer minRating) {
        if (minRating < 1 || minRating > 5) {
            throw new IllegalArgumentException("Minimum rating must be between 1 and 5");
        }
        return suppliersRepository.findByMinRating(minRating);
    }

    /**
     * Validate supplier data
     */
    private void validateSupplier(Suppliers supplier) {
        if (supplier.getName() == null || supplier.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Supplier name is required");
        }

        if (supplier.getCode() == null || supplier.getCode().trim().isEmpty()) {
            throw new IllegalArgumentException("Supplier code is required");
        }

        if (supplier.getRating() != null && (supplier.getRating() < 1 || supplier.getRating() > 5)) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }

        if (supplier.getCreditLimit() != null && supplier.getCreditLimit().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Credit limit cannot be negative");
        }

        if (supplier.getMinimumOrderAmount() != null && supplier.getMinimumOrderAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Minimum order amount cannot be negative");
        }

        if (supplier.getLeadTimeDays() != null && supplier.getLeadTimeDays() < 0) {
            throw new IllegalArgumentException("Lead time days cannot be negative");
        }
    }

    /**
     * Bulk activate suppliers
     */
    @Transactional
    public void bulkActivateSuppliers(List<Long> supplierIds) {
        for (Long id : supplierIds) {
            findById(id).ifPresent(supplier -> {
                supplier.setActive(true);
                suppliersRepository.save(supplier);
            });
        }
        log.info("Bulk activated {} suppliers", supplierIds.size());
    }

    /**
     * Bulk deactivate suppliers
     */
    @Transactional
    public void bulkDeactivateSuppliers(List<Long> supplierIds) {
        for (Long id : supplierIds) {
            findById(id).ifPresent(supplier -> {
                supplier.setActive(false);
                suppliersRepository.save(supplier);
            });
        }
        log.info("Bulk deactivated {} suppliers", supplierIds.size());
    }

    /**
     * Check if supplier exists by code
     */
    @Transactional(readOnly = true)
    public boolean existsByCode(String code) {
        return suppliersRepository.existsByCode(code);
    }

    /**
     * Check if supplier exists by tax ID
     */
    @Transactional(readOnly = true)
    public boolean existsByTaxId(String taxId) {
        return suppliersRepository.existsByTaxId(taxId);
    }
}
