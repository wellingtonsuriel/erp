package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.customers.Customers;
import com.pos_onlineshop.hybrid.enums.PaymentMethod;
import com.pos_onlineshop.hybrid.enums.SaleType;
import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.sales.Sales;
import com.pos_onlineshop.hybrid.sales.SalesRepository;
import com.pos_onlineshop.hybrid.shop.Shop;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SalesService {

    private final SalesRepository salesRepository;

    /**
     * Create a new sale
     */
    public Sales createSale(Sales sale) {
        log.info("Creating new sale for product: {}", sale.getProduct() != null ? sale.getProduct().getName() : "Unknown");

        // Validate required fields
        validateSale(sale);

        Sales savedSale = salesRepository.save(sale);
        log.info("Successfully created sale with ID: {}", savedSale.getId());

        return savedSale;
    }

    /**
     * Update an existing sale
     */
    public Sales updateSale(Long id, Sales saleDetails) {
        return salesRepository.findById(id)
                .map(sale -> {
                    if (saleDetails.getShop() != null) {
                        sale.setShop(saleDetails.getShop());
                    }
                    if (saleDetails.getCustomer() != null) {
                        sale.setCustomer(saleDetails.getCustomer());
                    }
                    if (saleDetails.getProduct() != null) {
                        sale.setProduct(saleDetails.getProduct());
                    }
                    if (saleDetails.getQuantity() != null) {
                        if (saleDetails.getQuantity() <= 0) {
                            throw new IllegalArgumentException("Quantity must be greater than 0");
                        }
                        sale.setQuantity(saleDetails.getQuantity());
                    }
                    if (saleDetails.getCurrency() != null) {
                        sale.setCurrency(saleDetails.getCurrency());
                    }
                    if (saleDetails.getUnitPrice() != null) {
                        if (saleDetails.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0) {
                            throw new IllegalArgumentException("Unit price must be greater than 0");
                        }
                        sale.setUnitPrice(saleDetails.getUnitPrice());
                    }
                    if (saleDetails.getPaymentMethod() != null) {
                        sale.setPaymentMethod(saleDetails.getPaymentMethod());
                    }
                    if (saleDetails.getSaleType() != null) {
                        sale.setSaleType(saleDetails.getSaleType());
                    }

                    Sales updated = salesRepository.save(sale);
                    log.info("Updated sale with ID: {}", updated.getId());
                    return updated;
                })
                .orElseThrow(() -> new RuntimeException("Sale not found: " + id));
    }

    /**
     * Delete a sale
     */
    public void deleteSale(Long id) {
        Optional<Sales> saleOpt = salesRepository.findById(id);
        if (saleOpt.isPresent()) {
            salesRepository.deleteById(id);
            log.info("Deleted sale with ID: {}", id);
        } else {
            throw new RuntimeException("Sale not found: " + id);
        }
    }

    // CRUD Read Operations

    @Transactional(readOnly = true)
    public Optional<Sales> findById(Long id) {
        return salesRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Sales> findAll() {
        return salesRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Page<Sales> findAll(Pageable pageable) {
        return salesRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<Sales> findByShop(Shop shop) {
        return salesRepository.findByShop(shop);
    }

    @Transactional(readOnly = true)
    public List<Sales> findByCustomer(Customers customer) {
        return salesRepository.findByCustomer(customer);
    }

    @Transactional(readOnly = true)
    public List<Sales> findByProduct(Product product) {
        return salesRepository.findByProduct(product);
    }

    @Transactional(readOnly = true)
    public List<Sales> findBySaleType(SaleType saleType) {
        return salesRepository.findBySaleType(saleType);
    }

    @Transactional(readOnly = true)
    public List<Sales> findByPaymentMethod(PaymentMethod paymentMethod) {
        return salesRepository.findByPaymentMethod(paymentMethod);
    }

    @Transactional(readOnly = true)
    public List<Sales> findByShopAndSaleType(Shop shop, SaleType saleType) {
        return salesRepository.findByShopAndSaleType(shop, saleType);
    }

    @Transactional(readOnly = true)
    public List<Sales> findByShopAndPaymentMethod(Shop shop, PaymentMethod paymentMethod) {
        return salesRepository.findByShopAndPaymentMethod(shop, paymentMethod);
    }

    @Transactional(readOnly = true)
    public List<Sales> findByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return salesRepository.findByDateRange(startDate, endDate);
    }

    @Transactional(readOnly = true)
    public List<Sales> findByShopAndDateRange(Shop shop, LocalDateTime startDate, LocalDateTime endDate) {
        return salesRepository.findByShopAndDateRange(shop, startDate, endDate);
    }

    @Transactional(readOnly = true)
    public List<Sales> findByCustomerAndDateRange(Customers customer, LocalDateTime startDate, LocalDateTime endDate) {
        return salesRepository.findByCustomerAndDateRange(customer, startDate, endDate);
    }

    @Transactional(readOnly = true)
    public List<Sales> findRecentSalesByShop(Shop shop) {
        return salesRepository.findRecentSalesByShop(shop);
    }

    @Transactional(readOnly = true)
    public List<Sales> findRecentSalesByCustomer(Customers customer) {
        return salesRepository.findRecentSalesByCustomer(customer);
    }

    // Analytics and Reporting

    @Transactional(readOnly = true)
    public BigDecimal getTotalSalesByShop(Shop shop) {
        BigDecimal total = salesRepository.getTotalSalesByShop(shop);
        return total != null ? total : BigDecimal.ZERO;
    }

    @Transactional(readOnly = true)
    public BigDecimal getTotalSalesByShopAndDateRange(Shop shop, LocalDateTime startDate, LocalDateTime endDate) {
        BigDecimal total = salesRepository.getTotalSalesByShopAndDateRange(shop, startDate, endDate);
        return total != null ? total : BigDecimal.ZERO;
    }

    @Transactional(readOnly = true)
    public BigDecimal getTotalSalesByCustomer(Customers customer) {
        BigDecimal total = salesRepository.getTotalSalesByCustomer(customer);
        return total != null ? total : BigDecimal.ZERO;
    }

    @Transactional(readOnly = true)
    public Long countSalesByShopAndDateRange(Shop shop, LocalDateTime startDate, LocalDateTime endDate) {
        return salesRepository.countSalesByShopAndDateRange(shop, startDate, endDate);
    }

    /**
     * Get total sales for today for a specific shop
     */
    @Transactional(readOnly = true)
    public BigDecimal getTodaySalesByShop(Shop shop) {
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime endOfDay = LocalDateTime.now().withHour(23).withMinute(59).withSecond(59).withNano(999999999);
        return getTotalSalesByShopAndDateRange(shop, startOfDay, endOfDay);
    }

    /**
     * Get sales count for today for a specific shop
     */
    @Transactional(readOnly = true)
    public Long getTodaySalesCountByShop(Shop shop) {
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime endOfDay = LocalDateTime.now().withHour(23).withMinute(59).withSecond(59).withNano(999999999);
        return countSalesByShopAndDateRange(shop, startOfDay, endOfDay);
    }

    /**
     * Validate sale data
     */
    private void validateSale(Sales sale) {
        if (sale.getShop() == null) {
            throw new IllegalArgumentException("Shop is required");
        }

        if (sale.getProduct() == null) {
            throw new IllegalArgumentException("Product is required");
        }

        if (sale.getQuantity() == null || sale.getQuantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than 0");
        }

        if (sale.getCurrency() == null) {
            throw new IllegalArgumentException("Currency is required");
        }

        if (sale.getUnitPrice() == null || sale.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Unit price must be greater than 0");
        }

        if (sale.getPaymentMethod() == null) {
            throw new IllegalArgumentException("Payment method is required");
        }

        if (sale.getSaleType() == null) {
            throw new IllegalArgumentException("Sale type is required");
        }
    }
}
