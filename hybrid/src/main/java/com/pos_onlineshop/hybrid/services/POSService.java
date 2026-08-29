package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.cashier.Cashier;
import com.pos_onlineshop.hybrid.cashierSessions.CashierSession;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.dtos.DailySummary;
import com.pos_onlineshop.hybrid.dtos.QuickSaleItem;
import com.pos_onlineshop.hybrid.dtos.Receipt;
import com.pos_onlineshop.hybrid.enums.FinancialEventType;
import com.pos_onlineshop.hybrid.enums.GLSourceModule;
import com.pos_onlineshop.hybrid.enums.OrderStatus;
import com.pos_onlineshop.hybrid.enums.PaymentMethod;
import com.pos_onlineshop.hybrid.enums.SalesChannel;
import com.pos_onlineshop.hybrid.gl.FinancialEvent;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.orderLines.OrderLine;
import com.pos_onlineshop.hybrid.orders.Order;
import com.pos_onlineshop.hybrid.orders.OrderRepository;
import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.selling_price.SellingPrice;
import com.pos_onlineshop.hybrid.shop.Shop;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class POSService {

    private final OrderService orderService;
    private final ProductService productService;
    private final OrderRepository orderRepository;
    private final ShopInventoryService shopInventoryService;
    private final AccountancyService accountancyService;
    private final SellingPriceService sellingPriceService;
    private final GLPostingService glPostingService;
    private final CurrencyService currencyService;
    private final com.pos_onlineshop.hybrid.journalEntry.JournalEntryRepository journalEntryRepository;
    private final InventoryValuationService inventoryValuationService;

    @Transactional
    public Order processQuickSale(List<QuickSaleItem> items, PaymentMethod paymentMethod,
                                  BigDecimal cashGiven, CashierSession session) {

        Cashier cashier = session.getCashier();
        Shop shop = session.getShop();
        Currency shopCurrency = shop.getDefaultCurrency(); // Get shop's default currency

        Order order = Order.builder()
                .user(null) // No user for POS sales
                .cashier(cashier)
                .cashierSession(session)
                .shop(shop)
                .currency(shopCurrency) // Set order currency
                .salesChannel(SalesChannel.POS)
                .paymentMethod(paymentMethod)
                .storeLocation(shop.getName())
                .status(OrderStatus.COMPLETED)
                .receiptNumber(generateReceiptNumber())
                .build();

        for (QuickSaleItem item : items) {
            Product product = productService.findById(item.getProductId())
                    .orElseThrow(() -> new RuntimeException("Product not found"));

            // Check shop inventory using the corrected method
            if (!shopInventoryService.isInStock(shop.getId(), item.getProductId(), item.getQuantity())) {
                throw new RuntimeException("Insufficient stock in shop for: " + product.getName());
            }

            // Get the selling price for this product in this shop
            SellingPrice sellingPrice = sellingPriceService.getCurrentPrice(product, shop)
                    .orElseThrow(() -> new RuntimeException("No selling price found for product: " + product.getName() + " in shop: " + shop.getName()));

            OrderLine orderLine = OrderLine.builder()
                    .quantity(item.getQuantity())
                    .cashier(cashier) // Set the cashier who processed this line
                    .cashierSession(session) // Set the session
                    .build();

            // Pass the SellingPrice and currency to copyProductDetails
            orderLine.copyProductDetails(sellingPrice, shopCurrency);

            order.addOrderLine(orderLine);

            // Remove from shop inventory using the corrected method
            shopInventoryService.reduceStock(shop.getId(), item.getProductId(), item.getQuantity());
        }

        if (paymentMethod == PaymentMethod.CASH && cashGiven != null) {
            order.setCashGiven(cashGiven);
            order.setChangeAmount(cashGiven.subtract(order.getTotalAmount()));
        }

        Order savedOrder = orderRepository.save(order);

        // Cost basis for COGS: real FIFO consumption of this shop's cost layers, computed
        // after the order has an id so each consumed unit's audit movement carries a real
        // reference. Tracks whether a real per-line cost was found for every line, so the GL
        // event only carries a COGS figure when it is fully known - never a partial,
        // understated one.
        BigDecimal totalCost = BigDecimal.ZERO;
        boolean costKnownForAllLines = true;
        for (OrderLine orderLine : savedOrder.getOrderLines()) {
            InventoryValuationService.CostResult costResult = inventoryValuationService.getCostForSale(
                    shop, orderLine.getProduct(), orderLine.getQuantity(), "ORDER-" + savedOrder.getId());
            if (costResult.isFullyCosted()) {
                orderLine.setUnitCost(costResult.getTotalCost().divide(
                        BigDecimal.valueOf(orderLine.getQuantity()), 4, java.math.RoundingMode.HALF_UP));
                totalCost = totalCost.add(costResult.getTotalCost());
            } else {
                costKnownForAllLines = false;
            }
        }

        accountancyService.createOrderAccountingEntries(savedOrder);
        accountancyService.createPaymentAccountingEntries(savedOrder);

        JournalEntry glEntry = postSaleToGeneralLedger(savedOrder, paymentMethod, totalCost, costKnownForAllLines, cashier);

        log.info("Processed POS sale {} at shop {} by cashier {} - GL entry #{}",
                savedOrder.getId(), shop.getName(), cashier.getFullName(), glEntry.getEntryNumber());
        return savedOrder;
    }

    /**
     * Emits the FinancialEvent for a completed POS order and posts it to the GL, inside the
     * same transaction as the order/stock write above - see GLPostingService's class comment
     * for why this must never become a second, separately-failable call.
     *
     * This runs alongside the existing AccountancyService calls above, not instead of them -
     * see the GL design report's migration plan (parallel posting until the new ledger's
     * totals are verified against the old one, then the legacy calls are retired).
     */
    private JournalEntry postSaleToGeneralLedger(Order savedOrder, PaymentMethod paymentMethod,
                                                  BigDecimal totalCost, boolean costKnownForAllLines,
                                                  Cashier cashier) {
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
                .eventType(paymentMethod == PaymentMethod.CASH
                        ? FinancialEventType.POS_CASH_SALE
                        : FinancialEventType.POS_NON_CASH_SALE)
                .sourceModule(GLSourceModule.POS)
                .sourceReferenceType("ORDER")
                .sourceReferenceId(savedOrder.getId())
                .idempotencyKey("POS-SALE-" + savedOrder.getId())
                .eventDate(LocalDate.now())
                .description("POS sale, receipt " + savedOrder.getReceiptNumber())
                .shop(savedOrder.getShop())
                .currency(currency)
                .exchangeRate(exchangeRate)
                .grossAmount(gross)
                .netAmount(net)
                .taxAmount(tax)
                .costAmount(costKnownForAllLines ? totalCost : null)
                .postedBy(cashier != null ? cashier.getFullName() : "system")
                .build();

        return glPostingService.post(event);
    }


    public Product findProductByBarcode(String barcode) {
        return productService.findByBarcode(barcode)
                .orElseThrow(() -> new RuntimeException("Product not found with barcode: " + barcode));
    }

    public DailySummary getDailySummary(LocalDate date, Long shopId) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();

        List<Order> dailyOrders = orderRepository.findByOrderDateBetween(startOfDay, endOfDay).stream()
                .filter(o -> o.getSalesChannel() == SalesChannel.POS)
                .filter(o -> shopId == null || (o.getShop() != null && o.getShop().getId().equals(shopId)))
                .toList();

        Map<PaymentMethod, BigDecimal> revenueByMethod = dailyOrders.stream()
                .collect(Collectors.groupingBy(
                        Order::getPaymentMethod,
                        Collectors.reducing(BigDecimal.ZERO, Order::getTotalAmount, BigDecimal::add)
                ));

        Map<String, Integer> topProducts = dailyOrders.stream()
                .flatMap(order -> order.getOrderLines().stream())
                .collect(Collectors.groupingBy(
                        OrderLine::getProductName,
                        Collectors.summingInt(OrderLine::getQuantity)
                ));

        // Get top 5 products
        Map<String, Integer> top5Products = topProducts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));

        BigDecimal totalRevenue = revenueByMethod.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal cashInDrawer = revenueByMethod.getOrDefault(PaymentMethod.CASH, BigDecimal.ZERO);

        // Count refunds/voids
        int refundsProcessed = (int) dailyOrders.stream()
                .filter(o -> o.getStatus() == OrderStatus.CANCELLED)
                .count();

        BigDecimal refundTotal = dailyOrders.stream()
                .filter(o -> o.getStatus() == OrderStatus.CANCELLED)
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Find top cashier
        String topCashier = dailyOrders.stream()
                .filter(o -> o.getCashier() != null)
                .collect(Collectors.groupingBy(
                        o -> o.getCashier().getFullName(),
                        Collectors.counting()
                ))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("N/A");

        return DailySummary.builder()
                .date(date)
                .storeLocation(shopId != null ? "Shop " + shopId : "All Shops")
                .totalTransactions(dailyOrders.size())
                .totalRevenue(totalRevenue)
                .revenueByPaymentMethod(revenueByMethod)
                .topSellingProducts(top5Products)
                .cashInDrawer(cashInDrawer)
                .refundsProcessed(refundsProcessed)
                .refundTotal(refundTotal)
                .topCashier(topCashier)
                .build();
    }

    public Receipt generateReceipt(Long orderId) {
        Order order = orderRepository.findByIdWithOrderLines(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        List<Receipt.ReceiptLine> lines = order.getOrderLines().stream()
                .map(line -> Receipt.ReceiptLine.builder()
                        .productName(line.getProductName())
                        .quantity(line.getQuantity())
                        .unitPrice(line.getUnitPrice())
                        .lineTotal(line.getSubtotal())
                        .build())
                .collect(Collectors.toList());

        Shop shop = order.getShop();

        return Receipt.builder()
                .receiptNumber(order.getReceiptNumber())
                .storeName(shop != null ? shop.getName() : "SalesPoint Store")
                .storeAddress(shop != null ? shop.getAddress() : "")
                .storePhone(shop != null ? shop.getPhoneNumber() : "")
                .timestamp(order.getOrderDate())
                .cashierName(order.getCashier() != null ? order.getCashier().getFullName() : "System")
                .lines(lines)
                .subtotal(order.getTotalAmount().subtract(order.getTaxAmount()))
                .tax(order.getTaxAmount())
                .total(order.getTotalAmount())
                .paymentMethod(order.getPaymentMethod())
                .cashGiven(order.getCashGiven())
                .change(order.getChangeAmount())
                .thankYouMessage("Thank you for shopping with us!")
                .barcode(order.getReceiptNumber())
                .build();
    }

    @Transactional
    public void voidTransaction(Long orderId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new RuntimeException("Order already voided");
        }

        // Return items to inventory
        for (OrderLine line : order.getOrderLines()) {
            shopInventoryService.addStock(
                    order.getShop().getId(),
                    line.getProduct().getId(),
                    line.getQuantity()
            );
        }

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        // Create refund accounting entries (legacy - runs alongside the GL reversal below,
        // same parallel-posting rationale as the sale path)
        accountancyService.createRefundAccountingEntries(order);

        // Reverse the original GL entry line-by-line rather than posting a generic refund
        // pair, per the "prefer an exact reversal" requirement. Orders that predate the GL
        // (or weren't a POS sale) simply have no matching entry - void still succeeds, the
        // GL side is just skipped, since stock and the legacy accounting entry are the
        // authoritative effects for those.
        journalEntryRepository.findByIdempotencyKey("POS-SALE-" + orderId).ifPresentOrElse(
                originalEntry -> glPostingService.reverse(originalEntry, LocalDate.now(), reason,
                        order.getCashier() != null ? order.getCashier().getFullName() : "system"),
                () -> log.info("No GL entry found for order {} - void proceeds without a GL reversal", orderId));

        log.info("Voided order {} - Reason: {}", orderId, reason);
    }

    public void openCashDrawer() {
        log.info("Opening cash drawer");
        // Hardware integration would go here
    }

    private String generateReceiptNumber() {
        return "REC-" + System.currentTimeMillis() + "-" +
                UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }


}