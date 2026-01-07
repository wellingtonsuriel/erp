package com.pos_onlineshop.hybrid.controllers;


import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.dtos.CreateOrderRequest;
import com.pos_onlineshop.hybrid.dtos.DailySummary;
import com.pos_onlineshop.hybrid.dtos.UpdateStatusRequest;
import com.pos_onlineshop.hybrid.enums.OrderStatus;
import com.pos_onlineshop.hybrid.enums.PaymentMethod;
import com.pos_onlineshop.hybrid.enums.SalesChannel;
import com.pos_onlineshop.hybrid.orders.Order;
import com.pos_onlineshop.hybrid.services.CurrencyService;
import com.pos_onlineshop.hybrid.services.OrderService;
import com.pos_onlineshop.hybrid.services.POSService;
import com.pos_onlineshop.hybrid.services.UserAccountService;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class OrderController {

    private final OrderService orderService;
    private final UserAccountService userAccountService;
    private final CurrencyService currencyService;
    private final POSService posService;

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Order> createOrder(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody CreateOrderRequest request) {
        UserAccount user = userAccountService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Currency orderCurrency = currencyService.findByCode(request.getCurrencyCode())
                .orElse(currencyService.getBaseCurrency());

        try {
            Order order = orderService.createOrderFromCart(
                    user,
                    request.getShippingAddress(),
                    request.getPaymentMethod(),
                    request.getSalesChannel() != null ? request.getSalesChannel() : SalesChannel.ONLINE,
                    orderCurrency
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(order);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Order> getOrder(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return orderService.findById(id)
                .filter(order -> canAccessOrder(order, userDetails))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/my-orders")
    @PreAuthorize("hasRole('USER')")
    public Page<Order> getMyOrders(
            @AuthenticationPrincipal UserDetails userDetails,
            Pageable pageable) {
        UserAccount user = userAccountService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        return orderService.findByUser(user, pageable);
    }

    @GetMapping("/statistics/revenue")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> getRevenueStatistics(
            @RequestParam(required = false) String currencyCode) {

        Currency currency = currencyCode != null ?
                currencyService.findByCode(currencyCode).orElse(currencyService.getBaseCurrency()) :
                currencyService.getBaseCurrency();

        Map<String, BigDecimal> revenues = Map.of(
                "confirmed", orderService.calculateRevenue(OrderStatus.CONFIRMED, currency),
                "delivered", orderService.calculateRevenue(OrderStatus.DELIVERED, currency),
                "completed", orderService.calculateRevenue(OrderStatus.COMPLETED, currency),
                "total", orderService.calculateRevenue(OrderStatus.DELIVERED, currency)
                        .add(orderService.calculateRevenue(OrderStatus.COMPLETED, currency))
        );

        Map<String, String> currencyInfo = Map.of(
                "code", currency.getCode(),
                "symbol", currency.getSymbol(),
                "name", currency.getName()
        );

        return Map.of(
                "currency", currencyInfo,
                "revenues", revenues
        );
    }
    // Other existing endpoints remain the same...
    @GetMapping("/status/{status}")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Order> getOrdersByStatus(@PathVariable OrderStatus status) {
        return orderService.findByStatus(status);
    }

    @GetMapping("/channel/{channel}")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Order> getOrdersByChannel(@PathVariable SalesChannel channel) {
        return orderService.findBySalesChannel(channel);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Order> updateOrderStatus(
            @PathVariable Long id,
            @RequestBody UpdateStatusRequest request) {
        try {
            Order updated = orderService.updateOrderStatus(id, request.getStatus());
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/statistics/recent")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Long> getRecentOrderStats() {
        return Map.of(
                "last7Days", orderService.countRecentOrders(7),
                "last30Days", orderService.countRecentOrders(30),
                "last90Days", orderService.countRecentOrders(90)
        );
    }

    @GetMapping("/statistics/channels")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Object[]> getChannelStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        return orderService.getSalesChannelStats(startDate, endDate);
    }

    @GetMapping("/statistics/top-products")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Object[]> getTopProducts() {
        return orderService.getMostOrderedProducts();
    }

    private boolean canAccessOrder(Order order, UserDetails userDetails) {
        if (order.getUser() != null && order.getUser().getUsername().equals(userDetails.getUsername())) {
            return true;
        }
        return userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") ||
                        a.getAuthority().equals("ROLE_CASHIER"));
    }


}
