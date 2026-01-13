package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.currency.CurrencyRepository;
import com.pos_onlineshop.hybrid.dtos.CreateTaxRequest;
import com.pos_onlineshop.hybrid.dtos.TaxResponse;
import com.pos_onlineshop.hybrid.dtos.UpdateTaxRequest;
import com.pos_onlineshop.hybrid.enums.TaxCalculationType;
import com.pos_onlineshop.hybrid.enums.TaxNature;
import com.pos_onlineshop.hybrid.mappers.TaxMapper;
import com.pos_onlineshop.hybrid.tax.Tax;
import com.pos_onlineshop.hybrid.tax.TaxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service class for managing taxes.
 * Handles tax CRUD operations and business logic.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TaxService {

    private final TaxRepository taxRepository;
    private final CurrencyRepository currencyRepository;
    private final TaxMapper taxMapper;

    /**
     * Create a new tax from request DTO.
     *
     * @param request the CreateTaxRequest
     * @return the created TaxResponse
     */
    public TaxResponse createTaxFromRequest(CreateTaxRequest request) {
        log.info("Creating new tax: {}", request.getTaxName());

        // Validate that tax name doesn't already exist
        if (taxRepository.existsByTaxName(request.getTaxName())) {
            throw new IllegalArgumentException("Tax with name '" + request.getTaxName() + "' already exists");
        }

        // Validate currency for FIXED taxes
        Currency currency = null;
        if (request.getTaxCalculationType() == TaxCalculationType.FIXED) {
            if (request.getCurrencyId() == null) {
                throw new IllegalArgumentException("Currency is required for FIXED tax calculation type");
            }
            currency = currencyRepository.findById(request.getCurrencyId())
                    .orElseThrow(() -> new IllegalArgumentException("Currency not found with ID: " + request.getCurrencyId()));
        }

        // Build and save the tax
        Tax tax = Tax.builder()
                .taxNature(request.getTaxNature())
                .taxName(request.getTaxName())
                .taxCalculationType(request.getTaxCalculationType())
                .taxValue(request.getTaxValue())
                .currency(currency)
                .active(request.getActive())
                .build();

        Tax savedTax = taxRepository.save(tax);
        log.info("Tax created successfully with ID: {}", savedTax.getTaxId());

        return taxMapper.toResponse(savedTax);
    }

    /**
     * Create a new tax directly from entity.
     *
     * @param tax the Tax entity
     * @return the saved Tax entity
     */
    public Tax createTax(Tax tax) {
        log.info("Creating new tax: {}", tax.getTaxName());
        validateTax(tax);
        return taxRepository.save(tax);
    }

    /**
     * Update an existing tax from request DTO.
     *
     * @param taxId   the tax ID
     * @param request the UpdateTaxRequest
     * @return the updated TaxResponse
     */
    public TaxResponse updateTaxFromRequest(Long taxId, UpdateTaxRequest request) {
        log.info("Updating tax with ID: {}", taxId);

        Tax tax = taxRepository.findById(taxId)
                .orElseThrow(() -> new IllegalArgumentException("Tax not found with ID: " + taxId));

        // Update fields if provided
        if (request.getTaxNature() != null) {
            tax.setTaxNature(request.getTaxNature());
        }

        if (request.getTaxName() != null) {
            // Check if new name conflicts with existing tax
            if (!tax.getTaxName().equals(request.getTaxName()) &&
                    taxRepository.existsByTaxName(request.getTaxName())) {
                throw new IllegalArgumentException("Tax with name '" + request.getTaxName() + "' already exists");
            }
            tax.setTaxName(request.getTaxName());
        }

        if (request.getTaxCalculationType() != null) {
            tax.setTaxCalculationType(request.getTaxCalculationType());
        }

        if (request.getTaxValue() != null) {
            tax.setTaxValue(request.getTaxValue());
        }

        if (request.getCurrencyId() != null) {
            Currency currency = currencyRepository.findById(request.getCurrencyId())
                    .orElseThrow(() -> new IllegalArgumentException("Currency not found with ID: " + request.getCurrencyId()));
            tax.setCurrency(currency);
        }

        if (request.getActive() != null) {
            tax.setActive(request.getActive());
        }

        // Validate before saving
        validateTax(tax);

        Tax updatedTax = taxRepository.save(tax);
        log.info("Tax updated successfully: {}", updatedTax.getTaxId());

        return taxMapper.toResponse(updatedTax);
    }

    /**
     * Update an existing tax directly from entity.
     *
     * @param taxId the tax ID
     * @param tax   the Tax entity with updated values
     * @return the updated Tax entity
     */
    public Tax updateTax(Long taxId, Tax tax) {
        log.info("Updating tax with ID: {}", taxId);

        if (!taxRepository.existsById(taxId)) {
            throw new IllegalArgumentException("Tax not found with ID: " + taxId);
        }

        tax.setTaxId(taxId);
        validateTax(tax);
        return taxRepository.save(tax);
    }

    /**
     * Delete a tax by ID.
     *
     * @param taxId the tax ID
     */
    public void deleteTax(Long taxId) {
        log.info("Deleting tax with ID: {}", taxId);

        if (!taxRepository.existsById(taxId)) {
            throw new IllegalArgumentException("Tax not found with ID: " + taxId);
        }

        taxRepository.deleteById(taxId);
        log.info("Tax deleted successfully: {}", taxId);
    }

    /**
     * Get a tax by ID.
     *
     * @param taxId the tax ID
     * @return the Tax entity
     */
    @Transactional(readOnly = true)
    public Tax findById(Long taxId) {
        return taxRepository.findById(taxId)
                .orElseThrow(() -> new IllegalArgumentException("Tax not found with ID: " + taxId));
    }

    /**
     * Get a tax response by ID.
     *
     * @param taxId the tax ID
     * @return the TaxResponse DTO
     */
    @Transactional(readOnly = true)
    public TaxResponse findByIdAsResponse(Long taxId) {
        Tax tax = findById(taxId);
        return taxMapper.toResponse(tax);
    }

    /**
     * Get all taxes.
     *
     * @return list of all taxes
     */
    @Transactional(readOnly = true)
    public List<Tax> findAll() {
        return taxRepository.findAll();
    }

    /**
     * Get all taxes as response DTOs.
     *
     * @return list of all TaxResponse DTOs
     */
    @Transactional(readOnly = true)
    public List<TaxResponse> findAllAsResponses() {
        return taxRepository.findAll().stream()
                .map(taxMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get all taxes with pagination.
     *
     * @param pageable pagination information
     * @return page of taxes
     */
    @Transactional(readOnly = true)
    public Page<Tax> findAll(Pageable pageable) {
        return taxRepository.findAll(pageable);
    }

    /**
     * Get all taxes with pagination as response DTOs.
     *
     * @param pageable pagination information
     * @return page of TaxResponse DTOs
     */
    @Transactional(readOnly = true)
    public Page<TaxResponse> findAllAsResponses(Pageable pageable) {
        return taxRepository.findAll(pageable)
                .map(taxMapper::toResponse);
    }

    /**
     * Get all active taxes.
     *
     * @return list of active taxes
     */
    @Transactional(readOnly = true)
    public List<Tax> findAllActive() {
        return taxRepository.findByActive(true);
    }

    /**
     * Get all active taxes as response DTOs.
     *
     * @return list of active TaxResponse DTOs
     */
    @Transactional(readOnly = true)
    public List<TaxResponse> findAllActiveAsResponses() {
        return taxRepository.findByActive(true).stream()
                .map(taxMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Find taxes by nature.
     *
     * @param taxNature the tax nature
     * @return list of taxes with the specified nature
     */
    @Transactional(readOnly = true)
    public List<Tax> findByTaxNature(TaxNature taxNature) {
        return taxRepository.findByTaxNature(taxNature);
    }

    /**
     * Find active taxes by nature.
     *
     * @param taxNature the tax nature
     * @return list of active taxes with the specified nature
     */
    @Transactional(readOnly = true)
    public List<Tax> findActiveTaxesByNature(TaxNature taxNature) {
        return taxRepository.findByTaxNatureAndActive(taxNature, true);
    }

    /**
     * Find taxes by calculation type.
     *
     * @param calculationType the tax calculation type
     * @return list of taxes with the specified calculation type
     */
    @Transactional(readOnly = true)
    public List<Tax> findByCalculationType(TaxCalculationType calculationType) {
        return taxRepository.findByTaxCalculationType(calculationType);
    }

    /**
     * Find active taxes by calculation type.
     *
     * @param calculationType the tax calculation type
     * @return list of active taxes with the specified calculation type
     */
    @Transactional(readOnly = true)
    public List<Tax> findActiveByCalculationType(TaxCalculationType calculationType) {
        return taxRepository.findByTaxCalculationTypeAndActive(calculationType, true);
    }

    /**
     * Find taxes by currency.
     *
     * @param currency the currency
     * @return list of taxes using the specified currency
     */
    @Transactional(readOnly = true)
    public List<Tax> findByCurrency(Currency currency) {
        return taxRepository.findByCurrency(currency);
    }

    /**
     * Find a tax by name.
     *
     * @param taxName the tax name
     * @return the Tax entity
     */
    @Transactional(readOnly = true)
    public Tax findByTaxName(String taxName) {
        return taxRepository.findByTaxName(taxName)
                .orElseThrow(() -> new IllegalArgumentException("Tax not found with name: " + taxName));
    }

    /**
     * Search taxes by name (partial match).
     *
     * @param name the search term
     * @return list of taxes matching the search term
     */
    @Transactional(readOnly = true)
    public List<Tax> searchByName(String name) {
        return taxRepository.findByTaxNameContaining(name);
    }

    /**
     * Validate tax entity.
     *
     * @param tax the tax to validate
     */
    private void validateTax(Tax tax) {
        if (tax.getTaxNature() == null) {
            throw new IllegalArgumentException("Tax nature is required");
        }

        if (tax.getTaxName() == null || tax.getTaxName().trim().isEmpty()) {
            throw new IllegalArgumentException("Tax name is required");
        }

        if (tax.getTaxCalculationType() == null) {
            throw new IllegalArgumentException("Tax calculation type is required");
        }

        if (tax.getTaxValue() == null) {
            throw new IllegalArgumentException("Tax value is required");
        }

        // Validate currency for FIXED taxes
        if (tax.getTaxCalculationType() == TaxCalculationType.FIXED && tax.getCurrency() == null) {
            throw new IllegalArgumentException("Currency is required for FIXED tax calculation type");
        }
    }
}
