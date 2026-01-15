package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.cart.Cart;
import com.pos_onlineshop.hybrid.cartItem.CartItem;
import com.pos_onlineshop.hybrid.cashier.Cashier;
import com.pos_onlineshop.hybrid.cashierSessions.CashierSession;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.customers.Customers;
import com.pos_onlineshop.hybrid.customers.CustomersRepository;
import com.pos_onlineshop.hybrid.dtos.OrderResponse;
import com.pos_onlineshop.hybrid.dtos.QuickSaleItem;
import com.pos_onlineshop.hybrid.dtos.UpdateOrderRequest;
import com.pos_onlineshop.hybrid.enums.OrderStatus;
import com.pos_onlineshop.hybrid.enums.PaymentMethod;
import com.pos_onlineshop.hybrid.enums.SalesChannel;
import com.pos_onlineshop.hybrid.mappers.OrderMapper;
import com.pos_onlineshop.hybrid.orderLines.OrderLine;
import com.pos_onlineshop.hybrid.orderLines.OrderLineRepository;
import com.pos_onlineshop.hybrid.orders.Order;
import com.pos_onlineshop.hybrid.orders.OrderRepository;
import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.selling_price.SellingPrice;
import com.pos_onlineshop.hybrid.shop.Shop;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderLineRepository orderLineRepository;
    private final CartService cartService;
    private final InventoryService inventoryService;
    private final AccountancyService accountancyService;
    private final CurrencyService currencyService;
    private final ProductService productService;
    private final SimpMessagingTemplate messagingTemplate;
    private final CustomersRepository customersRepository;
    private final OrderMapper orderMapper;
    private final ShopInventoryService shopInventoryService;
    private final ZimraService zimraService;
    private final SellingPriceService sellingPriceService;

    @Transactional
    public Order createOrderFromCart(UserAccount user, String shippingAddress,
                                     PaymentMethod paymentMethod, SalesChannel channel,
                                     Currency orderCurrency) {
        Cart cart = cartService.getCartByUser(user)
                .orElseThrow(() -> new RuntimeException("Cart not found for user"));

        if (cart.getCartItems().isEmpty()) {
            throw new RuntimeException("Cannot create order from empty cart");
        }

        // Get exchange rate if not base currency
        Currency baseCurrency = currencyService.getBaseCurrency();
        BigDecimal exchangeRate = null;
        if (!orderCurrency.equals(baseCurrency)) {
            exchangeRate = currencyService.getExchangeRate(baseCurrency, orderCurrency);
        }

        Order order = Order.builder()
                .user(user)
                .currency(orderCurrency)
                .exchangeRate(exchangeRate)
                .shippingAddress(shippingAddress)
                .paymentMethod(paymentMethod)
                .salesChannel(channel)
                .status(OrderStatus.PENDING)
                .build();

        for (CartItem cartItem : cart.getCartItems()) {
            Product product = cartItem.getProduct();

            // Check inventory
            if (!inventoryService.isInStock(product.getId(), cartItem.getQuantity())) {
                throw new RuntimeException("Insufficient stock for product: " + product.getName());
            }

            // Get product price in order currency
            // For online orders, get any available selling price for the product
            java.util.List<SellingPrice> productPrices = sellingPriceService.getProductPrices(product);
            SellingPrice sellingPrice = productPrices.stream()
                    .filter(SellingPrice::isCurrentlyEffective)
                    .filter(price -> price.getCurrency().equals(orderCurrency))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No selling price found for product: " + product.getName() + " in currency: " + orderCurrency.getCode()));

            OrderLine orderLine = OrderLine.builder()
                    .quantity(cartItem.getQuantity())
                    .build();
            orderLine.copyProductDetails(sellingPrice, orderCurrency);

            order.addOrderLine(orderLine);

            if (channel == SalesChannel.ONLINE) {
                inventoryService.reserveInventory(product.getId(), cartItem.getQuantity());
            } else {
                inventoryService.removeStock(product.getId(), cartItem.getQuantity());
            }
        }

        Order savedOrder = orderRepository.save(order);
        accountancyService.createOrderAccountingEntries(savedOrder);
        cartService.clearCart(user);

        // Notify new order
        messagingTemplate.convertAndSend("/topic/orders", savedOrder);

        log.info("Created order {} for user {} in currency {}",
                savedOrder.getId(), user.getUsername(), orderCurrency.getCode());
        return savedOrder;
    }

    @Transactional
    public Order processPOSSale(List<QuickSaleItem> items, PaymentMethod paymentMethod,
                                BigDecimal cashGiven, CashierSession session) {

        Cashier cashier = session.getCashier();
        Shop shop = session.getShop();
        Currency shopCurrency = shop.getDefaultCurrency();

        Order order = Order.builder()
                .user(null)
                .cashier(cashier)
                .cashierSession(session)
                .shop(shop)
                .currency(shopCurrency)
                .salesChannel(SalesChannel.POS)
                .paymentMethod(paymentMethod)
                .storeLocation(shop.getName())
                .status(OrderStatus.COMPLETED)
                .receiptNumber(generateReceiptNumber())
                .build();

        for (QuickSaleItem item : items) {
            Product product = productService.findById(item.getProductId())
                    .orElseThrow(() -> new RuntimeException("Product not found"));

            // Check shop inventory
            if (!shopInventoryService.isInStock(shop.getId(), item.getProductId(), item.getQuantity())) {
                throw new RuntimeException("Insufficient stock in shop for: " + product.getName());
            }

            // Get selling price for this product in this shop
            SellingPrice sellingPrice = sellingPriceService.getCurrentPrice(product, shop)
                    .orElseThrow(() -> new RuntimeException("No selling price found for product: " + product.getName() + " in shop: " + shop.getName()));

            OrderLine orderLine = OrderLine.builder()
                    .quantity(item.getQuantity())
                    .build();
            orderLine.copyProductDetails(sellingPrice, shopCurrency);

            order.addOrderLine(orderLine);

            // Remove from shop inventory
            shopInventoryService.reduceStock(shop.getId(), item.getProductId(), item.getQuantity());
        }

        if (paymentMethod == PaymentMethod.CASH && cashGiven != null) {
            order.setCashGiven(cashGiven);
            order.setChangeAmount(cashGiven.subtract(order.getTotalAmount()));
        }

        Order savedOrder = orderRepository.save(order);
        accountancyService.createOrderAccountingEntries(savedOrder);
        accountancyService.createPaymentAccountingEntries(savedOrder);

        // Auto-fiscalise POS transactions
        try {
            com.pos_onlineshop.hybrid.dtos.FiscaliseTransactionRequest fiscalRequest =
                com.pos_onlineshop.hybrid.dtos.FiscaliseTransactionRequest.builder()
                    .orderId(savedOrder.getId())
                    .shopId(shop.getId())
                    .documentType(com.pos_onlineshop.hybrid.enums.FiscalDocumentType.FISCAL_RECEIPT)
                    .build();
            zimraService.fiscaliseOrder(savedOrder.getId(), fiscalRequest);
            log.info("Auto-fiscalised POS order {}", savedOrder.getId());
        } catch (Exception e) {
            log.warn("Failed to auto-fiscalise POS order {}: {}", savedOrder.getId(), e.getMessage());
        }

        log.info("Processed POS sale {} at shop {} in currency {}",
                savedOrder.getId(), shop.getName(), shopCurrency.getCode());
        return savedOrder;
    }

    public Optional<Order> findById(Long id) {
        return orderRepository.findByIdWithOrderLines(id);
    }

    public Page<Order> findByUser(UserAccount user, Pageable pageable) {
        return orderRepository.findByUser(user, pageable);
    }

    public List<Order> findByStatus(OrderStatus status) {
        return orderRepository.findByStatus(status);
    }

    public List<Order> findBySalesChannel(SalesChannel channel) {
        return orderRepository.findBySalesChannel(channel);
    }

    public Order updateOrderStatus(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        OrderStatus oldStatus = order.getStatus();
        order.setStatus(newStatus);

        if (newStatus == OrderStatus.CONFIRMED && oldStatus == OrderStatus.PENDING) {
            // Convert reservations to actual stock deduction for online orders
            if (order.getSalesChannel() == SalesChannel.ONLINE) {
                for (OrderLine line : order.getOrderLines()) {
                    inventoryService.releaseReservation(line.getProduct().getId(), line.getQuantity());
                    inventoryService.removeStock(line.getProduct().getId(), line.getQuantity());
                }
            }
            accountancyService.createPaymentAccountingEntries(order);
        } else if (newStatus == OrderStatus.CANCELLED) {
            // Restore inventory
            for (OrderLine line : order.getOrderLines()) {
                if (order.getSalesChannel() == SalesChannel.ONLINE && oldStatus == OrderStatus.PENDING) {
                    inventoryService.releaseReservation(line.getProduct().getId(), line.getQuantity());
                } else if (order.getShop() != null) {
                    shopInventoryService.addStock(order.getShop().getId(), line.getProduct().getId(), line.getQuantity());
                } else {
                    inventoryService.addStock(line.getProduct().getId(), line.getQuantity());
                }
            }
            if (oldStatus != OrderStatus.PENDING) {
                accountancyService.createRefundAccountingEntries(order);
            }
        }

        return orderRepository.save(order);
    }

    public List<Object[]> getMostOrderedProducts() {
        return orderLineRepository.findMostOrderedProducts();
    }

    public BigDecimal calculateRevenue(OrderStatus status, Currency currency) {
        List<Order> orders = orderRepository.findByStatus(status);

        return orders.stream()
                .map(order -> {
                    if (order.getCurrency().equals(currency)) {
                        return order.getTotalAmount();
                    } else {
                        // Convert to requested currency
                        return currencyService.convert(
                                order.getTotalAmount(),
                                order.getCurrency(),
                                currency
                        );
                    }
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Long countRecentOrders(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return orderRepository.countOrdersSince(since);
    }

    public List<Object[]> getSalesChannelStats(LocalDateTime startDate, LocalDateTime endDate) {
        return orderRepository.getSalesChannelStats(startDate, endDate);
    }

    private String generateReceiptNumber() {
        return "REC-" + System.currentTimeMillis() + "-" +
                UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }

    // Add these methods to OrderService.java

    /**
     * Calculate revenue for a specific period
     */
    public BigDecimal calculatePeriodRevenue(LocalDateTime startDate, LocalDateTime endDate, Currency currency) {
        List<Order> orders = orderRepository.findByOrderDateBetween(startDate, endDate);

        return orders.stream()
                .filter(order -> order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.DELIVERED)
                .map(order -> {
                    if (order.getCurrency().equals(currency)) {
                        return order.getTotalAmount();
                    } else {
                        // Convert to requested currency
                        return currencyService.convert(
                                order.getTotalAmount(),
                                order.getCurrency(),
                                currency
                        );
                    }
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Count orders by status
     */
    public Long countOrdersByStatus(OrderStatus status) {
        return (long) orderRepository.findByStatus(status).size();
    }

    /**
     * Calculate daily revenue for specified number of days
     */
    public BigDecimal calculateDailyRevenue(int days, Currency currency) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        LocalDateTime endDate = LocalDateTime.now();
        return calculatePeriodRevenue(startDate, endDate, currency);
    }

    /**
     * Calculate weekly revenue for specified number of weeks
     */
    public BigDecimal calculateWeeklyRevenue(int weeks, Currency currency) {
        LocalDateTime startDate = LocalDateTime.now().minusWeeks(weeks);
        LocalDateTime endDate = LocalDateTime.now();
        return calculatePeriodRevenue(startDate, endDate, currency);
    }

    /**
     * Calculate monthly revenue for specified number of months
     */
    public BigDecimal calculateMonthlyRevenue(int months, Currency currency) {
        LocalDateTime startDate = LocalDateTime.now().minusMonths(months);
        LocalDateTime endDate = LocalDateTime.now();
        return calculatePeriodRevenue(startDate, endDate, currency);
    }

    /**
     * Calculate today's revenue
     */
    public BigDecimal calculateTodayRevenue(Currency currency) {
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime endOfDay = LocalDateTime.now().withHour(23).withMinute(59).withSecond(59).withNano(999999999);
        return calculatePeriodRevenue(startOfDay, endOfDay, currency);
    }

    /**
     * Calculate this week's revenue
     */
    public BigDecimal calculateThisWeekRevenue(Currency currency) {
        LocalDateTime startOfWeek = LocalDateTime.now().minusDays(LocalDateTime.now().getDayOfWeek().getValue() - 1)
                .withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime endOfWeek = LocalDateTime.now();
        return calculatePeriodRevenue(startOfWeek, endOfWeek, currency);
    }

    /**
     * Calculate this month's revenue
     */
    public BigDecimal calculateThisMonthRevenue(Currency currency) {
        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1)
                .withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime endOfMonth = LocalDateTime.now();
        return calculatePeriodRevenue(startOfMonth, endOfMonth, currency);
    }

    /**
     * Get revenue breakdown by status for a period
     */
    public Map<OrderStatus, BigDecimal> getRevenueBreakdownByStatus(LocalDateTime startDate, LocalDateTime endDate, Currency currency) {
        List<Order> orders = orderRepository.findByOrderDateBetween(startDate, endDate);

        return orders.stream()
                .collect(Collectors.groupingBy(
                        Order::getStatus,
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                order -> {
                                    if (order.getCurrency().equals(currency)) {
                                        return order.getTotalAmount();
                                    } else {
                                        return currencyService.convert(
                                                order.getTotalAmount(),
                                                order.getCurrency(),
                                                currency
                                        );
                                    }
                                },
                                BigDecimal::add
                        )
                ));
    }

    /**
     * Get order count breakdown by status for a period
     */
    public Map<OrderStatus, Long> getOrderCountBreakdownByStatus(LocalDateTime startDate, LocalDateTime endDate) {
        List<Order> orders = orderRepository.findByOrderDateBetween(startDate, endDate);

        return orders.stream()
                .collect(Collectors.groupingBy(
                        Order::getStatus,
                        Collectors.counting()
                ));
    }

    /**
     * Update an existing order from DTO
     */
    public OrderResponse updateOrderFromRequest(Long id, UpdateOrderRequest request) {
        return orderRepository.findById(id)
                .map(order -> {
                    // Update status if provided
                    if (request.getStatus() != null) {
                        // Use the existing updateOrderStatus method for status changes
                        // as it contains business logic for inventory management
                        order = updateOrderStatus(id, request.getStatus());
                    }

                    // Update payment method if provided
                    if (request.getPaymentMethod() != null) {
                        order.setPaymentMethod(request.getPaymentMethod());
                    }

                    // Update shipping address if provided
                    if (request.getShippingAddress() != null) {
                        order.setShippingAddress(request.getShippingAddress());
                    }

                    // Update customer if provided
                    if (request.getCustomerId() != null) {
                        Customers customer = customersRepository.findById(request.getCustomerId())
                                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + request.getCustomerId()));
                        order.setCustomer(customer);
                    }

                    // Update cash given if provided (for POS orders)
                    if (request.getCashGiven() != null && order.getPaymentMethod() == PaymentMethod.CASH) {
                        order.setCashGiven(request.getCashGiven());
                        order.setChangeAmount(request.getCashGiven().subtract(order.getTotalAmount()));
                    }

                    Order updated = orderRepository.save(order);
                    log.info("Updated order with ID: {}", updated.getId());
                    return orderMapper.toResponse(updated);
                })
                .orElseThrow(() -> new RuntimeException("Order not found: " + id));
    }

    /**
     * Delete an order
     */
    public void deleteOrder(Long id) {
        Optional<Order> orderOpt = orderRepository.findById(id);
        if (orderOpt.isPresent()) {
            Order order = orderOpt.get();

            // Restore inventory before deletion
            if (order.getStatus() != OrderStatus.CANCELLED) {
                for (OrderLine line : order.getOrderLines()) {
                    if (order.getSalesChannel() == SalesChannel.ONLINE && order.getStatus() == OrderStatus.PENDING) {
                        inventoryService.releaseReservation(line.getProduct().getId(), line.getQuantity());
                    } else if (order.getShop() != null) {
                        shopInventoryService.addStock(order.getShop().getId(), line.getProduct().getId(), line.getQuantity());
                    } else {
                        inventoryService.addStock(line.getProduct().getId(), line.getQuantity());
                    }
                }
            }

            orderRepository.deleteById(id);
            log.info("Deleted order with ID: {}", id);
        } else {
            throw new RuntimeException("Order not found: " + id);
        }
    }

    /**
     * Get order by ID as DTO
     */
    @Transactional(readOnly = true)
    public Optional<OrderResponse> findByIdAsResponse(Long id) {
        return orderRepository.findByIdWithOrderLines(id)
                .map(orderMapper::toResponse);
    }

    /**
     * Get all orders as DTOs
     */
    @Transactional(readOnly = true)
    public List<OrderResponse> findAllAsResponses() {
        return orderRepository.findAll().stream()
                .map(orderMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get all orders as DTOs with pagination
     */
    @Transactional(readOnly = true)
    public Page<OrderResponse> findAllAsResponses(Pageable pageable) {
        return orderRepository.findAll(pageable)
                .map(orderMapper::toResponse);
    }

    /**
     * Get orders by user as DTOs
     */
    @Transactional(readOnly = true)
    public Page<OrderResponse> findByUserAsResponses(UserAccount user, Pageable pageable) {
        return orderRepository.findByUser(user, pageable)
                .map(orderMapper::toResponse);
    }

    /**
     * Get orders by status as DTOs
     */
    @Transactional(readOnly = true)
    public List<OrderResponse> findByStatusAsResponses(OrderStatus status) {
        return orderRepository.findByStatus(status).stream()
                .map(orderMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get orders by sales channel as DTOs
     */
    @Transactional(readOnly = true)
    public List<OrderResponse> findBySalesChannelAsResponses(SalesChannel channel) {
        return orderRepository.findBySalesChannel(channel).stream()
                .map(orderMapper::toResponse)
                .collect(Collectors.toList());
    }
}