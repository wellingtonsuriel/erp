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
import com.pos_onlineshop.hybrid.enums.FinancialEventType;
import com.pos_onlineshop.hybrid.enums.GLSourceModule;
import com.pos_onlineshop.hybrid.enums.OrderStatus;
import com.pos_onlineshop.hybrid.enums.PaymentMethod;
import com.pos_onlineshop.hybrid.enums.SalesChannel;
import com.pos_onlineshop.hybrid.enums.ShopType;
import com.pos_onlineshop.hybrid.gl.FinancialEvent;
import com.pos_onlineshop.hybrid.mappers.OrderMapper;
import com.pos_onlineshop.hybrid.orderLines.OrderLine;
import com.pos_onlineshop.hybrid.orderLines.OrderLineRepository;
import com.pos_onlineshop.hybrid.orders.Order;
import com.pos_onlineshop.hybrid.orders.OrderRepository;
import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.selling_price.SellingPrice;
import com.pos_onlineshop.hybrid.shop.Shop;
import com.pos_onlineshop.hybrid.shop.ShopRepository;
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
    private final ShopRepository shopRepository;
    private final ZimraService zimraService;
    private final SellingPriceService sellingPriceService;
    private final GLPostingService glPostingService;
    private final InventoryValuationService inventoryValuationService;

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

        Shop fulfillmentShop = resolveFulfillmentShop(cart.getCartItems());

        Order order = Order.builder()
                .user(user)
                .currency(orderCurrency)
                .exchangeRate(exchangeRate)
                .shippingAddress(shippingAddress)
                .paymentMethod(paymentMethod)
                .salesChannel(channel)
                .shop(fulfillmentShop)
                .status(OrderStatus.PENDING)
                .build();

        for (CartItem cartItem : cart.getCartItems()) {
            Product product = cartItem.getProduct();

            // Check availability against the authoritative per-shop stock model - the same
            // InventoryTotal balance POS deducts from, so the two channels can never both
            // believe the same physical units are free to sell.
            if (!shopInventoryService.isInStock(fulfillmentShop.getId(), product.getId(), cartItem.getQuantity())) {
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
                // Reserved, not yet physically removed - see updateOrderStatus for where a
                // reservation is committed (CONFIRMED) or released (CANCELLED before confirm).
                shopInventoryService.reserveStock(fulfillmentShop.getId(), product.getId(), cartItem.getQuantity());
            } else {
                shopInventoryService.reduceStock(fulfillmentShop.getId(), product.getId(), cartItem.getQuantity());
            }
        }

        Order savedOrder = orderRepository.save(order);
        accountancyService.createOrderAccountingEntries(savedOrder);
        postOnlineOrderToGeneralLedger(savedOrder, paymentMethod);
        cartService.clearCart(user);

        // Notify new order
        messagingTemplate.convertAndSend("/topic/orders", savedOrder);

        log.info("Created order {} for user {} in currency {}, fulfilled from shop {}",
                savedOrder.getId(), user.getUsername(), orderCurrency.getCode(), fulfillmentShop.getCode());
        return savedOrder;
    }

    /**
     * Picks which shop's physical stock an online/cart-based order draws from. There is no
     * per-order shop-selection field in CreateOrderRequest today, so tier 1 ("explicit
     * fulfillment shop") from the hardening spec is not reachable yet - documented here
     * rather than silently invented. Falls through:
     * 1. The configured default ONLINE-type shop (ShopType.ONLINE), if one is active.
     * 2. The first active shop (any type) that has sufficient available stock for every
     *    line in the cart.
     * Throws if neither exists - never silently picks a shop that can't fulfill the order.
     */
    private Shop resolveFulfillmentShop(List<CartItem> cartItems) {
        Optional<Shop> defaultOnlineShop = shopRepository.findByActiveTrueAndType(ShopType.ONLINE)
                .stream().findFirst();
        if (defaultOnlineShop.isPresent()) {
            return defaultOnlineShop.get();
        }

        return shopRepository.findByActiveTrue().stream()
                .filter(candidate -> cartItems.stream().allMatch(item ->
                        shopInventoryService.isInStock(candidate.getId(), item.getProduct().getId(), item.getQuantity())))
                .findFirst()
                .orElseThrow(() -> new RuntimeException(
                        "No shop is configured or available with sufficient stock to fulfill this order"));
    }

    /**
     * Every new online order now always has a shop (see resolveFulfillmentShop), but an
     * order created before this fix may still have shop == null in the database - ddl-auto
     * schema changes don't retroactively populate application data. These fall back to the
     * deprecated InventoryItem pool they were actually reserved/deducted from, so historical
     * orders keep working, rather than throwing on a null shop.
     */
    private void legacyConfirmWithoutShop(Order order) {
        log.warn("Order {} has no shop recorded (pre-inventory-hardening); confirming via the "
                + "deprecated InventoryItem pool", order.getId());
        for (OrderLine line : order.getOrderLines()) {
            inventoryService.releaseReservation(line.getProduct().getId(), line.getQuantity());
            inventoryService.removeStock(line.getProduct().getId(), line.getQuantity());
        }
    }

    private void legacyRestoreWithoutShop(Order order, OrderLine line) {
        log.warn("Order {} has no shop recorded (pre-inventory-hardening); restoring via the "
                + "deprecated InventoryItem pool", order.getId());
        inventoryService.addStock(line.getProduct().getId(), line.getQuantity());
    }

    /**
     * Every online order carries a definite, non-null PaymentMethod today - this codebase has
     * no "place order now, pay later" concept (no Order.paid flag, no deferred-payment flow),
     * so ONLINE_ORDER_UNPAID (credit) is never emitted here; treating an order as unpaid just
     * because its payment method happened to be absent would be inventing a distinction the
     * data doesn't actually make. If/when a real credit-order flow is added, that call site
     * should emit ONLINE_ORDER_UNPAID against 1100 Accounts Receivable instead.
     *
     * costAmount is deliberately left null here: revenue is recognized at checkout/payment
     * time (this method), but the stock backing this order is only reserved at this point,
     * not yet physically committed (see createOrderFromCart) - so there is nothing to cost
     * yet. COGS is posted separately, in its own journal entry, when the reservation is
     * actually committed - see postOnlineOrderCogsToGeneralLedger, called from
     * updateOrderStatus's CONFIRMED branch. This mirrors the master accounting rule that a
     * reservation alone must never touch the GL: only an actual physical stock reduction
     * recognizes COGS.
     */
    private void postOnlineOrderToGeneralLedger(Order savedOrder, PaymentMethod paymentMethod) {
        Currency currency = savedOrder.getCurrency();
        Currency baseCurrency = currencyService.getBaseCurrency();
        BigDecimal exchangeRate = BigDecimal.ONE;
        if (currency != null && !currency.equals(baseCurrency)) {
            exchangeRate = currencyService.getExchangeRate(currency, baseCurrency);
        }

        BigDecimal gross = savedOrder.getTotalAmount();
        BigDecimal tax = savedOrder.getTaxAmount() != null ? savedOrder.getTaxAmount() : BigDecimal.ZERO;
        BigDecimal net = gross.subtract(tax);

        FinancialEvent event = FinancialEvent.builder()
                .eventType(FinancialEventType.ONLINE_ORDER_PAID)
                .sourceModule(GLSourceModule.ORDER)
                .sourceReferenceType("ORDER")
                .sourceReferenceId(savedOrder.getId())
                .idempotencyKey("ONLINE-ORDER-" + savedOrder.getId())
                .eventDate(java.time.LocalDate.now())
                .description("Online order " + savedOrder.getId() + " (" + paymentMethod + ")")
                .shop(savedOrder.getShop())
                .currency(currency)
                .exchangeRate(exchangeRate)
                .grossAmount(gross)
                .netAmount(net)
                .taxAmount(tax)
                .costAmount(null)
                .postedBy("system")
                .build();

        glPostingService.post(event);
    }

    /**
     * Posts COGS/Inventory for an online order at the moment its reservation is actually
     * committed to a physical stock reduction (see updateOrderStatus's CONFIRMED branch) -
     * a separate journal entry from postOnlineOrderToGeneralLedger's revenue/tax entry, since
     * the two happen at genuinely different times for this channel. Reuses the
     * ONLINE_ORDER_PAID posting rule (its COST line pair is exactly Dr 5000 COGS / Cr 1200
     * Inventory) with GROSS/NET/TAX left at zero, so only the cost pair posts - see
     * GLPostingService.post's "amount <= 0 is skipped" behavior. Real FIFO cost, never
     * guessed: if any line's cost layers don't fully cover its quantity (see
     * InventoryValuationService.CostResult.fullyCosted), no COGS entry is posted at all for
     * this order rather than posting a partial, understated one.
     */
    private void postOnlineOrderCogsToGeneralLedger(Order order) {
        BigDecimal totalCost = BigDecimal.ZERO;
        boolean fullyCosted = true;
        for (OrderLine line : order.getOrderLines()) {
            InventoryValuationService.CostResult result = inventoryValuationService.getCostForSale(
                    order.getShop(), line.getProduct(), line.getQuantity(), "ORDER-" + order.getId());
            if (result.isFullyCosted()) {
                line.setUnitCost(result.getTotalCost().divide(
                        BigDecimal.valueOf(line.getQuantity()), 4, java.math.RoundingMode.HALF_UP));
                totalCost = totalCost.add(result.getTotalCost());
            } else {
                fullyCosted = false;
            }
        }

        if (!fullyCosted || totalCost.compareTo(BigDecimal.ZERO) <= 0) {
            if (!fullyCosted) {
                log.warn("Online order {} confirmed with incomplete cost layer coverage - COGS not posted", order.getId());
            }
            return;
        }

        Currency currency = order.getCurrency();
        Currency baseCurrency = currencyService.getBaseCurrency();
        BigDecimal exchangeRate = currency != null && !currency.equals(baseCurrency)
                ? currencyService.getExchangeRate(currency, baseCurrency) : BigDecimal.ONE;

        FinancialEvent event = FinancialEvent.builder()
                .eventType(FinancialEventType.ONLINE_ORDER_PAID)
                .sourceModule(GLSourceModule.ORDER)
                .sourceReferenceType("ORDER")
                .sourceReferenceId(order.getId())
                .idempotencyKey("ONLINE-ORDER-COGS-" + order.getId())
                .eventDate(java.time.LocalDate.now())
                .description("Online order " + order.getId() + " fulfilled - COGS")
                .shop(order.getShop())
                .currency(currency)
                .exchangeRate(exchangeRate)
                .grossAmount(BigDecimal.ZERO)
                .netAmount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .costAmount(totalCost)
                .postedBy("system")
                .build();

        glPostingService.post(event);
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
            // Convert reservations to actual, permanent stock deduction for online orders.
            // The oldStatus == PENDING guard makes this idempotent: a second CONFIRMED call
            // on an already-CONFIRMED order sees oldStatus == CONFIRMED and skips this block.
            if (order.getSalesChannel() == SalesChannel.ONLINE && order.getShop() != null) {
                for (OrderLine line : order.getOrderLines()) {
                    shopInventoryService.commitReservedStock(order.getShop().getId(), line.getProduct().getId(), line.getQuantity());
                }
                postOnlineOrderCogsToGeneralLedger(order);
            } else if (order.getSalesChannel() == SalesChannel.ONLINE) {
                legacyConfirmWithoutShop(order);
            }
            accountancyService.createPaymentAccountingEntries(order);
        } else if (newStatus == OrderStatus.CANCELLED) {
            // Restore inventory
            for (OrderLine line : order.getOrderLines()) {
                if (order.getSalesChannel() == SalesChannel.ONLINE && oldStatus == OrderStatus.PENDING && order.getShop() != null) {
                    shopInventoryService.releaseReservation(order.getShop().getId(), line.getProduct().getId(), line.getQuantity());
                } else if (order.getShop() != null) {
                    shopInventoryService.addStock(order.getShop().getId(), line.getProduct().getId(), line.getQuantity());
                } else {
                    legacyRestoreWithoutShop(order, line);
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
                    if (order.getSalesChannel() == SalesChannel.ONLINE && order.getStatus() == OrderStatus.PENDING && order.getShop() != null) {
                        shopInventoryService.releaseReservation(order.getShop().getId(), line.getProduct().getId(), line.getQuantity());
                    } else if (order.getShop() != null) {
                        shopInventoryService.addStock(order.getShop().getId(), line.getProduct().getId(), line.getQuantity());
                    } else {
                        legacyRestoreWithoutShop(order, line);
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
     * Cancels every ONLINE order still PENDING (never confirmed/paid) since before cutoff -
     * the abandoned-cart case: a customer reserved stock at checkout and never completed
     * payment, and that stock needs to come back to the available pool for other customers.
     * Releases each line's reservation the same way deleteOrder() does, then marks the order
     * CANCELLED rather than deleting it, preserving the record instead of erasing it. Safe to
     * run repeatedly - an order this already cancelled no longer matches the PENDING query,
     * so re-running never double-releases a reservation. Returns the number of orders expired.
     */
    @Transactional
    public int expireStalePendingOrders(LocalDateTime cutoff) {
        List<Order> stale = orderRepository.findBySalesChannelAndStatusAndOrderDateBefore(
                SalesChannel.ONLINE, OrderStatus.PENDING, cutoff);
        for (Order order : stale) {
            if (order.getShop() != null) {
                for (OrderLine line : order.getOrderLines()) {
                    shopInventoryService.releaseReservation(order.getShop().getId(), line.getProduct().getId(), line.getQuantity());
                }
            }
            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);
        }
        if (!stale.isEmpty()) {
            log.info("Expired {} stale PENDING online order(s) older than {}", stale.size(), cutoff);
        }
        return stale.size();
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