package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.customers.Customers;
import com.pos_onlineshop.hybrid.enums.PaymentMethod;
import com.pos_onlineshop.hybrid.enums.SaleType;
import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.sales.Sales;
import com.pos_onlineshop.hybrid.services.SalesService;
import com.pos_onlineshop.hybrid.services.CustomersService;
import com.pos_onlineshop.hybrid.services.ProductService;
import com.pos_onlineshop.hybrid.services.ShopService;
import com.pos_onlineshop.hybrid.shop.Shop;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class SalesController {

    private final SalesService salesService;
    private final ShopService shopService;
    private final CustomersService customersService;
    private final ProductService productService;

    /**
     * Get all sales
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('CASHIER')")
    public ResponseEntity<List<Sales>> getAllSales() {
        List<Sales> sales = salesService.findAll();
        return ResponseEntity.ok(sales);
    }

    /**
     * Get all sales with pagination
     */
    @GetMapping("/paginated")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CASHIER')")
    public ResponseEntity<Page<Sales>> getAllSalesPaginated(Pageable pageable) {
        Page<Sales> sales = salesService.findAll(pageable);
        return ResponseEntity.ok(sales);
    }

    /**
     * Get sale by ID
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CASHIER')")
    public ResponseEntity<Sales> getSaleById(@PathVariable Long id) {
        return salesService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get sales by shop ID
     */
    @GetMapping("/shop/{shopId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CASHIER')")
    public ResponseEntity<List<Sales>> getSalesByShop(@PathVariable Long shopId) {
        try {
            Shop shop = shopService.findById(shopId)
                    .orElseThrow(() -> new RuntimeException("Shop not found"));
            List<Sales> sales = salesService.findByShop(shop);
            return ResponseEntity.ok(sales);
        } catch (RuntimeException e) {
            log.error("Error fetching sales by shop", e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get sales by customer ID
     */
    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CASHIER')")
    public ResponseEntity<List<Sales>> getSalesByCustomer(@PathVariable Long customerId) {
        try {
            Customers customer = customersService.findById(customerId)
                    .orElseThrow(() -> new RuntimeException("Customer not found"));
            List<Sales> sales = salesService.findByCustomer(customer);
            return ResponseEntity.ok(sales);
        } catch (RuntimeException e) {
            log.error("Error fetching sales by customer", e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get sales by product ID
     */
    @GetMapping("/product/{productId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CASHIER')")
    public ResponseEntity<List<Sales>> getSalesByProduct(@PathVariable Long productId) {
        try {
            Product product = productService.findById(productId)
                    .orElseThrow(() -> new RuntimeException("Product not found"));
            List<Sales> sales = salesService.findByProduct(product);
            return ResponseEntity.ok(sales);
        } catch (RuntimeException e) {
            log.error("Error fetching sales by product", e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get sales by sale type
     */
    @GetMapping("/type/{saleType}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CASHIER')")
    public ResponseEntity<List<Sales>> getSalesBySaleType(@PathVariable SaleType saleType) {
        List<Sales> sales = salesService.findBySaleType(saleType);
        return ResponseEntity.ok(sales);
    }

    /**
     * Get sales by payment method
     */
    @GetMapping("/payment-method/{paymentMethod}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CASHIER')")
    public ResponseEntity<List<Sales>> getSalesByPaymentMethod(@PathVariable PaymentMethod paymentMethod) {
        List<Sales> sales = salesService.findByPaymentMethod(paymentMethod);
        return ResponseEntity.ok(sales);
    }

    /**
     * Get sales by date range
     */
    @GetMapping("/date-range")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CASHIER')")
    public ResponseEntity<List<Sales>> getSalesByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        List<Sales> sales = salesService.findByDateRange(startDate, endDate);
        return ResponseEntity.ok(sales);
    }

    /**
     * Get sales by shop and date range
     */
    @GetMapping("/shop/{shopId}/date-range")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CASHIER')")
    public ResponseEntity<List<Sales>> getSalesByShopAndDateRange(
            @PathVariable Long shopId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        try {
            Shop shop = shopService.findById(shopId)
                    .orElseThrow(() -> new RuntimeException("Shop not found"));
            List<Sales> sales = salesService.findByShopAndDateRange(shop, startDate, endDate);
            return ResponseEntity.ok(sales);
        } catch (RuntimeException e) {
            log.error("Error fetching sales by shop and date range", e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get recent sales by shop
     */
    @GetMapping("/shop/{shopId}/recent")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CASHIER')")
    public ResponseEntity<List<Sales>> getRecentSalesByShop(@PathVariable Long shopId) {
        try {
            Shop shop = shopService.findById(shopId)
                    .orElseThrow(() -> new RuntimeException("Shop not found"));
            List<Sales> sales = salesService.findRecentSalesByShop(shop);
            return ResponseEntity.ok(sales);
        } catch (RuntimeException e) {
            log.error("Error fetching recent sales by shop", e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get total sales by shop
     */
    @GetMapping("/shop/{shopId}/total")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CASHIER')")
    public ResponseEntity<Map<String, BigDecimal>> getTotalSalesByShop(@PathVariable Long shopId) {
        try {
            Shop shop = shopService.findById(shopId)
                    .orElseThrow(() -> new RuntimeException("Shop not found"));
            BigDecimal total = salesService.getTotalSalesByShop(shop);
            return ResponseEntity.ok(Map.of("total", total));
        } catch (RuntimeException e) {
            log.error("Error fetching total sales by shop", e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get total sales by shop and date range
     */
    @GetMapping("/shop/{shopId}/total/date-range")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CASHIER')")
    public ResponseEntity<Map<String, BigDecimal>> getTotalSalesByShopAndDateRange(
            @PathVariable Long shopId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        try {
            Shop shop = shopService.findById(shopId)
                    .orElseThrow(() -> new RuntimeException("Shop not found"));
            BigDecimal total = salesService.getTotalSalesByShopAndDateRange(shop, startDate, endDate);
            return ResponseEntity.ok(Map.of("total", total));
        } catch (RuntimeException e) {
            log.error("Error fetching total sales by shop and date range", e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get today's sales by shop
     */
    @GetMapping("/shop/{shopId}/today")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CASHIER')")
    public ResponseEntity<Map<String, Object>> getTodaySalesByShop(@PathVariable Long shopId) {
        try {
            Shop shop = shopService.findById(shopId)
                    .orElseThrow(() -> new RuntimeException("Shop not found"));
            BigDecimal total = salesService.getTodaySalesByShop(shop);
            Long count = salesService.getTodaySalesCountByShop(shop);
            return ResponseEntity.ok(Map.of(
                    "total", total,
                    "count", count
            ));
        } catch (RuntimeException e) {
            log.error("Error fetching today's sales by shop", e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get total sales by customer
     */
    @GetMapping("/customer/{customerId}/total")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CASHIER')")
    public ResponseEntity<Map<String, BigDecimal>> getTotalSalesByCustomer(@PathVariable Long customerId) {
        try {
            Customers customer = customersService.findById(customerId)
                    .orElseThrow(() -> new RuntimeException("Customer not found"));
            BigDecimal total = salesService.getTotalSalesByCustomer(customer);
            return ResponseEntity.ok(Map.of("total", total));
        } catch (RuntimeException e) {
            log.error("Error fetching total sales by customer", e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Create a new sale
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('CASHIER')")
    public ResponseEntity<Sales> createSale(@Valid @RequestBody Sales sale) {
        try {
            Sales created = salesService.createSale(sale);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            log.error("Validation error creating sale", e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error creating sale", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Update an existing sale
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Sales> updateSale(
            @PathVariable Long id,
            @Valid @RequestBody Sales saleDetails) {
        try {
            Sales updated = salesService.updateSale(id, saleDetails);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            log.error("Validation error updating sale", e);
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException e) {
            log.error("Error updating sale", e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Delete a sale
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteSale(@PathVariable Long id) {
        try {
            salesService.deleteSale(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            log.error("Error deleting sale", e);
            return ResponseEntity.notFound().build();
        }
    }
}
