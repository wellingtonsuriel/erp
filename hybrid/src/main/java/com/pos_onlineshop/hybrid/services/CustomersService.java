package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.customers.Customers;
import com.pos_onlineshop.hybrid.customers.CustomersRepository;
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
public class CustomersService {

    private final CustomersRepository customersRepository;

    /**
     * Create a new customer
     */
    public Customers createCustomer(Customers customer) {
        log.info("Creating new customer: {}", customer.getName());

        // Validate required fields
        validateCustomer(customer);

        // Check for duplicate code
        if (customer.getCode() != null && customersRepository.existsByCode(customer.getCode())) {
            throw new IllegalArgumentException("Customer with code " + customer.getCode() + " already exists");
        }

        // Check for duplicate tax ID
        if (customer.getTaxId() != null && customersRepository.existsByTaxId(customer.getTaxId())) {
            throw new IllegalArgumentException("Customer with tax ID " + customer.getTaxId() + " already exists");
        }

        // Check for duplicate email
        if (customer.getEmail() != null && customersRepository.existsByEmail(customer.getEmail())) {
            throw new IllegalArgumentException("Customer with email " + customer.getEmail() + " already exists");
        }

        Customers savedCustomer = customersRepository.save(customer);
        log.info("Successfully created customer: {} with ID: {}", savedCustomer.getName(), savedCustomer.getId());

        return savedCustomer;
    }

    /**
     * Update an existing customer
     */
    public Customers updateCustomer(Long id, Customers customerDetails) {
        return customersRepository.findById(id)
                .map(customer -> {
                    // Update basic fields
                    if (customerDetails.getName() != null) {
                        customer.setName(customerDetails.getName());
                    }
                    if (customerDetails.getCode() != null && !customerDetails.getCode().equals(customer.getCode())) {
                        if (customersRepository.existsByCode(customerDetails.getCode())) {
                            throw new IllegalArgumentException("Customer with code " + customerDetails.getCode() + " already exists");
                        }
                        customer.setCode(customerDetails.getCode());
                    }
                    if (customerDetails.getEmail() != null && !customerDetails.getEmail().equals(customer.getEmail())) {
                        if (customersRepository.existsByEmail(customerDetails.getEmail())) {
                            throw new IllegalArgumentException("Customer with email " + customerDetails.getEmail() + " already exists");
                        }
                        customer.setEmail(customerDetails.getEmail());
                    }
                    if (customerDetails.getPhoneNumber() != null) {
                        customer.setPhoneNumber(customerDetails.getPhoneNumber());
                    }
                    if (customerDetails.getMobileNumber() != null) {
                        customer.setMobileNumber(customerDetails.getMobileNumber());
                    }
                    if (customerDetails.getAddress() != null) {
                        customer.setAddress(customerDetails.getAddress());
                    }
                    if (customerDetails.getCity() != null) {
                        customer.setCity(customerDetails.getCity());
                    }
                    if (customerDetails.getState() != null) {
                        customer.setState(customerDetails.getState());
                    }
                    if (customerDetails.getCountry() != null) {
                        customer.setCountry(customerDetails.getCountry());
                    }
                    if (customerDetails.getPostalCode() != null) {
                        customer.setPostalCode(customerDetails.getPostalCode());
                    }
                    if (customerDetails.getTaxId() != null && !customerDetails.getTaxId().equals(customer.getTaxId())) {
                        if (customersRepository.existsByTaxId(customerDetails.getTaxId())) {
                            throw new IllegalArgumentException("Customer with tax ID " + customerDetails.getTaxId() + " already exists");
                        }
                        customer.setTaxId(customerDetails.getTaxId());
                    }
                    if (customerDetails.getPaymentTerms() != null) {
                        customer.setPaymentTerms(customerDetails.getPaymentTerms());
                    }
                    if (customerDetails.getCreditLimit() != null) {
                        customer.setCreditLimit(customerDetails.getCreditLimit());
                    }
                    if (customerDetails.getNotes() != null) {
                        customer.setNotes(customerDetails.getNotes());
                    }
                    if (customerDetails.getLoyaltyPoints() != null) {
                        customer.setLoyaltyPoints(customerDetails.getLoyaltyPoints());
                    }

                    Customers updated = customersRepository.save(customer);
                    log.info("Updated customer: {}", updated.getName());
                    return updated;
                })
                .orElseThrow(() -> new RuntimeException("Customer not found: " + id));
    }

    /**
     * Delete a customer
     */
    public void deleteCustomer(Long id) {
        Optional<Customers> customerOpt = customersRepository.findById(id);
        if (customerOpt.isPresent()) {
            Customers customer = customerOpt.get();
            customersRepository.deleteById(id);
            log.info("Deleted customer: {} with ID: {}", customer.getName(), id);
        } else {
            throw new RuntimeException("Customer not found: " + id);
        }
    }

    /**
     * Soft delete - deactivate customer
     */
    public void deactivateCustomer(Long id) {
        customersRepository.findById(id)
                .ifPresentOrElse(
                        customer -> {
                            customer.setActive(false);
                            customersRepository.save(customer);
                            log.info("Deactivated customer: {}", customer.getName());
                        },
                        () -> {
                            throw new RuntimeException("Customer not found: " + id);
                        }
                );
    }

    /**
     * Activate customer
     */
    public void activateCustomer(Long id) {
        customersRepository.findById(id)
                .ifPresentOrElse(
                        customer -> {
                            customer.setActive(true);
                            customersRepository.save(customer);
                            log.info("Activated customer: {}", customer.getName());
                        },
                        () -> {
                            throw new RuntimeException("Customer not found: " + id);
                        }
                );
    }

    /**
     * Verify customer
     */
    public void verifyCustomer(Long id) {
        customersRepository.findById(id)
                .ifPresentOrElse(
                        customer -> {
                            customer.setVerified(true);
                            customersRepository.save(customer);
                            log.info("Verified customer: {}", customer.getName());
                        },
                        () -> {
                            throw new RuntimeException("Customer not found: " + id);
                        }
                );
    }

    /**
     * Unverify customer
     */
    public void unverifyCustomer(Long id) {
        customersRepository.findById(id)
                .ifPresentOrElse(
                        customer -> {
                            customer.setVerified(false);
                            customersRepository.save(customer);
                            log.info("Unverified customer: {}", customer.getName());
                        },
                        () -> {
                            throw new RuntimeException("Customer not found: " + id);
                        }
                );
    }

    /**
     * Add loyalty points to customer
     */
    public Customers addLoyaltyPoints(Long id, Integer points) {
        if (points < 0) {
            throw new IllegalArgumentException("Points to add cannot be negative");
        }

        return customersRepository.findById(id)
                .map(customer -> {
                    customer.setLoyaltyPoints(customer.getLoyaltyPoints() + points);
                    Customers updated = customersRepository.save(customer);
                    log.info("Added {} loyalty points to customer {}. New total: {}", points, customer.getName(), updated.getLoyaltyPoints());
                    return updated;
                })
                .orElseThrow(() -> new RuntimeException("Customer not found: " + id));
    }

    /**
     * Redeem loyalty points from customer
     */
    public Customers redeemLoyaltyPoints(Long id, Integer points) {
        if (points < 0) {
            throw new IllegalArgumentException("Points to redeem cannot be negative");
        }

        return customersRepository.findById(id)
                .map(customer -> {
                    if (customer.getLoyaltyPoints() < points) {
                        throw new IllegalArgumentException("Insufficient loyalty points. Current: " + customer.getLoyaltyPoints() + ", Requested: " + points);
                    }
                    customer.setLoyaltyPoints(customer.getLoyaltyPoints() - points);
                    Customers updated = customersRepository.save(customer);
                    log.info("Redeemed {} loyalty points from customer {}. New total: {}", points, customer.getName(), updated.getLoyaltyPoints());
                    return updated;
                })
                .orElseThrow(() -> new RuntimeException("Customer not found: " + id));
    }

    // CRUD Read Operations

    @Transactional(readOnly = true)
    public Optional<Customers> findById(Long id) {
        return customersRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Customers> findByCode(String code) {
        return customersRepository.findByCode(code);
    }

    @Transactional(readOnly = true)
    public Optional<Customers> findByTaxId(String taxId) {
        return customersRepository.findByTaxId(taxId);
    }

    @Transactional(readOnly = true)
    public Optional<Customers> findByEmail(String email) {
        return customersRepository.findByEmail(email);
    }

    @Transactional(readOnly = true)
    public List<Customers> findAll() {
        return customersRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Page<Customers> findAll(Pageable pageable) {
        return customersRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<Customers> findActiveCustomers() {
        return customersRepository.findByActiveTrue();
    }

    @Transactional(readOnly = true)
    public List<Customers> findVerifiedCustomers() {
        return customersRepository.findByVerifiedTrue();
    }

    @Transactional(readOnly = true)
    public List<Customers> findActiveAndVerifiedCustomers() {
        return customersRepository.findByActiveTrueAndVerifiedTrue();
    }

    @Transactional(readOnly = true)
    public List<Customers> findAllActiveOrdered() {
        return customersRepository.findAllActiveOrdered();
    }

    @Transactional(readOnly = true)
    public List<Customers> findAllActiveAndVerifiedOrdered() {
        return customersRepository.findAllActiveAndVerifiedOrdered();
    }

    @Transactional(readOnly = true)
    public List<Customers> searchByNameOrCodeOrEmail(String searchTerm) {
        return customersRepository.searchByNameOrCodeOrEmail(searchTerm);
    }

    @Transactional(readOnly = true)
    public List<Customers> findByCountry(String country) {
        return customersRepository.findByCountry(country);
    }

    @Transactional(readOnly = true)
    public List<Customers> findByCity(String city) {
        return customersRepository.findByCity(city);
    }

    @Transactional(readOnly = true)
    public List<Customers> findByMinLoyaltyPoints(Integer minPoints) {
        if (minPoints < 0) {
            throw new IllegalArgumentException("Minimum loyalty points cannot be negative");
        }
        return customersRepository.findByMinLoyaltyPoints(minPoints);
    }

    /**
     * Validate customer data
     */
    private void validateCustomer(Customers customer) {
        if (customer.getName() == null || customer.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Customer name is required");
        }

        if (customer.getCode() == null || customer.getCode().trim().isEmpty()) {
            throw new IllegalArgumentException("Customer code is required");
        }

        if (customer.getCreditLimit() != null && customer.getCreditLimit().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Credit limit cannot be negative");
        }

        if (customer.getLoyaltyPoints() != null && customer.getLoyaltyPoints() < 0) {
            throw new IllegalArgumentException("Loyalty points cannot be negative");
        }
    }

    /**
     * Bulk activate customers
     */
    @Transactional
    public void bulkActivateCustomers(List<Long> customerIds) {
        for (Long id : customerIds) {
            findById(id).ifPresent(customer -> {
                customer.setActive(true);
                customersRepository.save(customer);
            });
        }
        log.info("Bulk activated {} customers", customerIds.size());
    }

    /**
     * Bulk deactivate customers
     */
    @Transactional
    public void bulkDeactivateCustomers(List<Long> customerIds) {
        for (Long id : customerIds) {
            findById(id).ifPresent(customer -> {
                customer.setActive(false);
                customersRepository.save(customer);
            });
        }
        log.info("Bulk deactivated {} customers", customerIds.size());
    }

    /**
     * Check if customer exists by code
     */
    @Transactional(readOnly = true)
    public boolean existsByCode(String code) {
        return customersRepository.existsByCode(code);
    }

    /**
     * Check if customer exists by tax ID
     */
    @Transactional(readOnly = true)
    public boolean existsByTaxId(String taxId) {
        return customersRepository.existsByTaxId(taxId);
    }

    /**
     * Check if customer exists by email
     */
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return customersRepository.existsByEmail(email);
    }
}
