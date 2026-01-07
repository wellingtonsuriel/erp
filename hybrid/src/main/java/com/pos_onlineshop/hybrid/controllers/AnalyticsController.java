package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.currency.Currency;

import com.pos_onlineshop.hybrid.enums.OrderStatus;
import com.pos_onlineshop.hybrid.services.*;

import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@CrossOrigin(origins = "*")
@Slf4j
public class AnalyticsController {

    private final OrderService orderService;
    private final InventoryService inventoryService;
    private final UserAccountService userAccountService;
    private final AccountancyService accountancyService;
    private final ShopInventoryService shopInventoryService;
    private final CurrencyService currencyService;

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboardData(
            @RequestParam(value = "currencyCode", defaultValue = "USD") String currencyCode,
            @RequestParam(value = "shopId", required = false) Long shopId) {

        Map<String, Object> dashboard = new HashMap<>();

        try {
            // Get the currency for revenue calculations
            Currency targetCurrency = currencyService.findByCode(currencyCode)
                    .orElse(currencyService.getDefaultCurrency());

            // Order statistics
            dashboard.put("todayOrders", orderService.countRecentOrders(1));
            dashboard.put("weekOrders", orderService.countRecentOrders(7));
            dashboard.put("monthOrders", orderService.countRecentOrders(30));

            // Revenue statistics with proper currency parameter
            dashboard.put("todayRevenue", orderService.calculateRevenue(OrderStatus.COMPLETED, targetCurrency));
            dashboard.put("pendingRevenue", orderService.calculateRevenue(OrderStatus.PENDING, targetCurrency));
            dashboard.put("totalRevenue", orderService.calculateRevenue(OrderStatus.COMPLETED, targetCurrency)
                    .add(orderService.calculateRevenue(OrderStatus.DELIVERED, targetCurrency)));

            // Inventory statistics
            if (shopId != null) {
                // Shop-specific inventory data
                dashboard.put("shopInventoryValue", shopInventoryService.calculateShopInventoryValue(shopId));
                dashboard.put("shopLowStockCount", shopInventoryService.getLowStockItems(shopId).size());
                dashboard.put("shopOverstockedCount", shopInventoryService.getOverstockedItems(shopId).size());
                dashboard.put("shopUnderstockedCount", shopInventoryService.getUnderstockedItems(shopId).size());
                dashboard.put("shopItemsNeedingRestock", shopInventoryService.getItemsNeedingRestock(shopId).size());
            } else {
                // System-wide inventory data using existing InventoryService
                dashboard.put("totalInventoryValue", inventoryService.calculateTotalInventoryValue());
                dashboard.put("lowStockCount", inventoryService.findLowStockItems().size());
            }

            // User statistics
            dashboard.put("totalUsers", userAccountService.findAllUsers().size());
            dashboard.put("activeUsers", userAccountService.findAllUsers().stream()
                    .filter(UserAccount::isEnabled).count());

            // Currency information
            dashboard.put("currency", Map.of(
                    "code", targetCurrency.getCode(),
                    "symbol", targetCurrency.getSymbol(),
                    "name", targetCurrency.getName()
            ));

            // Additional metrics
            dashboard.put("averageOrderValue", calculateAverageOrderValue(targetCurrency));
            dashboard.put("conversionRate", calculateOrderConversionRate());

            log.info("Dashboard data retrieved successfully for currency: {}, shopId: {}", currencyCode, shopId);
            return ResponseEntity.ok(dashboard);

        } catch (Exception e) {
            log.error("Error retrieving dashboard data", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to retrieve dashboard data: " + e.getMessage()));
        }
    }

    @GetMapping("/sales-trend")
    public ResponseEntity<Map<String, Object>> getSalesTrend(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(value = "currencyCode", defaultValue = "USD") String currencyCode) {

        try {
            LocalDateTime start = startDate.atStartOfDay();
            LocalDateTime end = endDate.plusDays(1).atStartOfDay();

            Currency targetCurrency = currencyService.findByCode(currencyCode)
                    .orElse(currencyService.getDefaultCurrency());

            Map<String, Object> trend = new HashMap<>();
            trend.put("channelStats", orderService.getSalesChannelStats(start, end));
            trend.put("topProducts", orderService.getMostOrderedProducts());
            trend.put("periodSummary", accountancyService.getPeriodicSummary());

            // Add revenue data for the period with currency support
            trend.put("periodRevenue", orderService.calculatePeriodRevenue(start, end, targetCurrency));
            trend.put("currency", targetCurrency.getCode());

            // Add growth comparison if available
            LocalDateTime previousStart = start.minusDays(endDate.toEpochDay() - startDate.toEpochDay() + 1);
            trend.put("previousPeriodRevenue", orderService.calculatePeriodRevenue(previousStart, start, targetCurrency));
            trend.put("growthRate", calculateGrowthRate(
                    orderService.calculatePeriodRevenue(previousStart, start, targetCurrency),
                    orderService.calculatePeriodRevenue(start, end, targetCurrency)
            ));

            return ResponseEntity.ok(trend);

        } catch (Exception e) {
            log.error("Error retrieving sales trend data", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to retrieve sales trend data: " + e.getMessage()));
        }
    }

    @GetMapping("/performance")
    public ResponseEntity<Map<String, Object>> getPerformanceMetrics(
            @RequestParam(value = "currencyCode", defaultValue = "USD") String currencyCode,
            @RequestParam(value = "shopId", required = false) Long shopId) {

        try {
            Currency targetCurrency = currencyService.findByCode(currencyCode)
                    .orElse(currencyService.getDefaultCurrency());

            Map<String, Object> performance = new HashMap<>();

            // Calculate conversion rate, average order value, etc.
            performance.put("averageOrderValue", calculateAverageOrderValue(targetCurrency));
            performance.put("conversionRate", calculateOrderConversionRate());
            performance.put("inventoryTurnover", calculateInventoryTurnover(shopId));

            // Additional performance metrics
            performance.put("orderFulfillmentRate", calculateOrderFulfillmentRate());
            performance.put("customerRetentionRate", calculateCustomerRetentionRate());
            performance.put("averageOrderProcessingTime", calculateAverageOrderProcessingTime());

            if (shopId != null) {
                performance.put("shopPerformance", getShopSpecificPerformance(shopId, targetCurrency));
            }

            performance.put("currency", targetCurrency.getCode());

            return ResponseEntity.ok(performance);

        } catch (Exception e) {
            log.error("Error retrieving performance metrics", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to retrieve performance metrics: " + e.getMessage()));
        }
    }

    @GetMapping("/revenue")
    public ResponseEntity<Map<String, Object>> getRevenueData(
            @RequestParam(value = "currencyCode", defaultValue = "USD") String currencyCode,
            @RequestParam(value = "days", defaultValue = "30") int days,
            @RequestParam(value = "shopId", required = false) Long shopId) {

        Map<String, Object> revenueData = new HashMap<>();

        try {
            Currency targetCurrency = currencyService.findByCode(currencyCode)
                    .orElse(currencyService.getDefaultCurrency());

            // Revenue by status
            revenueData.put("completedRevenue", orderService.calculateRevenue(OrderStatus.COMPLETED, targetCurrency));
            revenueData.put("pendingRevenue", orderService.calculateRevenue(OrderStatus.PENDING, targetCurrency));
            revenueData.put("deliveredRevenue", orderService.calculateRevenue(OrderStatus.DELIVERED, targetCurrency));
            revenueData.put("cancelledRevenue", orderService.calculateRevenue(OrderStatus.CANCELLED, targetCurrency));

            // Time-based revenue (if you have methods for this)
            revenueData.put("dailyRevenue", orderService.calculateDailyRevenue(days, targetCurrency));
            revenueData.put("weeklyRevenue", orderService.calculateWeeklyRevenue(days / 7, targetCurrency));
            revenueData.put("monthlyRevenue", orderService.calculateMonthlyRevenue(days / 30, targetCurrency));

            revenueData.put("currency", targetCurrency.getCode());

            if (shopId != null) {
                revenueData.put("shopSpecificRevenue", getShopRevenue(shopId, targetCurrency));
            }

            return ResponseEntity.ok(revenueData);

        } catch (Exception e) {
            log.error("Error retrieving revenue data", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to retrieve revenue data: " + e.getMessage()));
        }
    }

    @GetMapping("/inventory")
    public ResponseEntity<Map<String, Object>> getInventoryData(
            @RequestParam(value = "shopId", required = false) Long shopId) {

        Map<String, Object> inventoryData = new HashMap<>();

        try {
            if (shopId != null) {
                // Shop-specific inventory
                inventoryData.put("totalValue", shopInventoryService.calculateShopInventoryValue(shopId));
                inventoryData.put("lowStock", shopInventoryService.getLowStockItems(shopId));
                inventoryData.put("overstocked", shopInventoryService.getOverstockedItems(shopId));
                inventoryData.put("understocked", shopInventoryService.getUnderstockedItems(shopId));
                inventoryData.put("needsRestock", shopInventoryService.getItemsNeedingRestock(shopId));
                inventoryData.put("shopId", shopId);
            } else {
                // System-wide inventory using existing InventoryService
                inventoryData.put("totalValue", inventoryService.calculateTotalInventoryValue());
                inventoryData.put("lowStockItems", inventoryService.findLowStockItems());
                inventoryData.put("lowStockCount", inventoryService.findLowStockItems().size());
            }

            return ResponseEntity.ok(inventoryData);

        } catch (Exception e) {
            log.error("Error retrieving inventory data", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to retrieve inventory data: " + e.getMessage()));
        }
    }

    // Helper methods for calculations
    private BigDecimal calculateAverageOrderValue(Currency currency) {
        try {
            BigDecimal totalRevenue = orderService.calculateRevenue(OrderStatus.COMPLETED, currency);
            long totalOrders = orderService.countRecentOrders(Integer.MAX_VALUE);

            if (totalOrders > 0) {
                return totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, BigDecimal.ROUND_HALF_UP);
            }
            return BigDecimal.ZERO;
        } catch (Exception e) {
            log.warn("Error calculating average order value", e);
            return BigDecimal.ZERO;
        }
    }

    private double calculateOrderConversionRate() {
        try {
            // Placeholder implementation - adjust based on your business logic
            long completedOrders = orderService.countOrdersByStatus(OrderStatus.COMPLETED);
            long totalOrders = orderService.countRecentOrders(Integer.MAX_VALUE);

            if (totalOrders > 0) {
                return ((double) completedOrders / totalOrders) * 100;
            }
            return 0.0;
        } catch (Exception e) {
            log.warn("Error calculating conversion rate", e);
            return 0.0;
        }
    }

    private double calculateInventoryTurnover(Long shopId) {
        try {
            // Placeholder - implement based on your business logic
            // Inventory turnover = Cost of Goods Sold / Average Inventory Value
            return 0.0; // TODO: Implement inventory turnover calculation
        } catch (Exception e) {
            log.warn("Error calculating inventory turnover", e);
            return 0.0;
        }
    }

    private double calculateOrderFulfillmentRate() {
        try {
            long fulfilledOrders = orderService.countOrdersByStatus(OrderStatus.DELIVERED);
            long totalOrders = orderService.countRecentOrders(Integer.MAX_VALUE);

            if (totalOrders > 0) {
                return ((double) fulfilledOrders / totalOrders) * 100;
            }
            return 0.0;
        } catch (Exception e) {
            log.warn("Error calculating fulfillment rate", e);
            return 0.0;
        }
    }

    private double calculateCustomerRetentionRate() {
        try {
            // Placeholder - implement based on your customer analytics
            return 0.0; // TODO: Implement customer retention rate
        } catch (Exception e) {
            log.warn("Error calculating customer retention rate", e);
            return 0.0;
        }
    }

    private double calculateAverageOrderProcessingTime() {
        try {
            // Placeholder - implement based on order timestamps
            return 0.0; // TODO: Implement average processing time calculation
        } catch (Exception e) {
            log.warn("Error calculating average processing time", e);
            return 0.0;
        }
    }

    private Map<String, Object> getShopSpecificPerformance(Long shopId, Currency currency) {
        Map<String, Object> shopPerformance = new HashMap<>();
        // Placeholder - implement shop-specific performance metrics
        shopPerformance.put("shopId", shopId);
        shopPerformance.put("shopRevenue", getShopRevenue(shopId, currency));
        shopPerformance.put("shopOrderCount", 0); // TODO: Implement
        return shopPerformance;
    }

    private BigDecimal getShopRevenue(Long shopId, Currency currency) {
        try {
            // Placeholder - implement shop-specific revenue calculation
            return BigDecimal.ZERO; // TODO: Implement shop revenue calculation
        } catch (Exception e) {
            log.warn("Error calculating shop revenue", e);
            return BigDecimal.ZERO;
        }
    }

    private double calculateGrowthRate(BigDecimal previousValue, BigDecimal currentValue) {
        try {
            if (previousValue.compareTo(BigDecimal.ZERO) == 0) {
                return currentValue.compareTo(BigDecimal.ZERO) > 0 ? 100.0 : 0.0;
            }
            return currentValue.subtract(previousValue)
                    .divide(previousValue, 4, BigDecimal.ROUND_HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
        } catch (Exception e) {
            log.warn("Error calculating growth rate", e);
            return 0.0;
        }
    }
}