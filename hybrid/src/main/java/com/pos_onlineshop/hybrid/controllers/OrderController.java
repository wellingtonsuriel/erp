package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.dtos.CreateOrderRequest;
import com.pos_onlineshop.hybrid.dtos.OrderResponse;
import com.pos_onlineshop.hybrid.dtos.UpdateOrderRequest;
import com.pos_onlineshop.hybrid.enums.OrderStatus;
import com.pos_onlineshop.hybrid.enums.SalesChannel;
import com.pos_onlineshop.hybrid.orders.Order;
import com.pos_onlineshop.hybrid.services.CurrencyService;
import com.pos_onlineshop.hybrid.services.OrderService;
import com.pos_onlineshop.hybrid.services.UserAccountService;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class OrderController {

    private final OrderService orderService;
    private final UserAccountService userAccountService;
    private final CurrencyService currencyService;

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

    /**
     * Get all orders
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('CASHIER')")
    public ResponseEntity<List<OrderResponse>> getAllOrders() {
        List<OrderResponse> orders = orderService.findAllAsResponses();
        return ResponseEntity.ok(orders);
    }

    /**
     * Get all orders with pagination
     */
    @GetMapping("/paginated")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CASHIER')")
    public ResponseEntity<Page<OrderResponse>> getAllOrdersPaginated(Pageable pageable) {
        Page<OrderResponse> orders = orderService.findAllAsResponses(pageable);
        return ResponseEntity.ok(orders);
    }

    /**
     * Get order by ID
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<OrderResponse> getOrder(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return orderService.findByIdAsResponse(id)
                .filter(order -> canAccessOrder(order, userDetails))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get my orders
     */
    @GetMapping("/my-orders")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Page<OrderResponse>> getMyOrders(
            @AuthenticationPrincipal UserDetails userDetails,
            Pageable pageable) {
        UserAccount user = userAccountService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Page<OrderResponse> orders = orderService.findByUserAsResponses(user, pageable);
        return ResponseEntity.ok(orders);
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
    /**
     * Get orders by status
     */
    @GetMapping("/status/{status}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CASHIER')")
    public ResponseEntity<List<OrderResponse>> getOrdersByStatus(@PathVariable OrderStatus status) {
        List<OrderResponse> orders = orderService.findByStatusAsResponses(status);
        return ResponseEntity.ok(orders);
    }

    /**
     * Get orders by sales channel
     */
    @GetMapping("/channel/{channel}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CASHIER')")
    public ResponseEntity<List<OrderResponse>> getOrdersByChannel(@PathVariable SalesChannel channel) {
        List<OrderResponse> orders = orderService.findBySalesChannelAsResponses(channel);
        return ResponseEntity.ok(orders);
    }

    /**
     * Update an existing order
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrderResponse> updateOrder(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderRequest request) {
        try {
            OrderResponse updated = orderService.updateOrderFromRequest(id, request);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            log.error("Validation error updating order", e);
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException e) {
            log.error("Error updating order", e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Delete an order
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteOrder(@PathVariable Long id) {
        try {
            orderService.deleteOrder(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            log.error("Error deleting order", e);
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

    private boolean canAccessOrder(OrderResponse order, UserDetails userDetails) {
        if (order.getUsername() != null && order.getUsername().equals(userDetails.getUsername())) {
            return true;
        }
        return userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") ||
                        a.getAuthority().equals("ROLE_CASHIER"));
    }
}
