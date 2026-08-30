package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.dtos.ShopStockReport;
import com.pos_onlineshop.hybrid.dtos.StockSummaryReport;
import com.pos_onlineshop.hybrid.dtos.StockValueReport;
import com.pos_onlineshop.hybrid.dtos.TransferReport;
import com.pos_onlineshop.hybrid.inventory.InventoryItem;
import com.pos_onlineshop.hybrid.inventory.InventoryItemRepository;
import com.pos_onlineshop.hybrid.inventoryTotal.InventoryTotal;
import com.pos_onlineshop.hybrid.inventoryTotal.InventoryTotalRepository;
import com.pos_onlineshop.hybrid.inventoryTransfer.InventoryTransfer;
import com.pos_onlineshop.hybrid.inventoryTransfer.InventoryTransferRepository;
import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.shop.Shop;
import com.pos_onlineshop.hybrid.shop.ShopRepository;
import com.pos_onlineshop.hybrid.shopInventory.ShopInventory;
import com.pos_onlineshop.hybrid.shopInventory.ShopInventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class InventoryService {

    private final InventoryItemRepository inventoryRepository;
    private final InventoryTotalRepository inventoryTotalRepository;
    private final InventoryTransferRepository transferRepository;
    private final ShopRepository shopRepository;
    private final ShopInventoryRepository shopInventoryRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final InventoryValuationService inventoryValuationService;

    // ==================== Deprecated InventoryItem pool ====================
    // The methods below operate on InventoryItem, a global stock counter independent of
    // InventoryTotal (the authoritative per-shop model - see ShopInventoryService's class
    // comment). OrderService no longer calls these for new orders; retained only for
    // historical pre-hardening orders. Do not add new callers - use ShopInventoryService's
    // reserveStock/releaseReservation/commitReservedStock/addStock/reduceStock/isInStock
    // instead, all of which are shop-scoped against InventoryTotal.

    @Deprecated
    public InventoryItem initializeInventory(Product product, Integer initialQuantity) {
        log.info("Initializing inventory for product: {}", product.getName());

        InventoryItem inventory = InventoryItem.builder()
                .product(product)
                .quantity(initialQuantity)
                .reservedQuantity(0)
                .reorderLevel(10)
                .build();

        return inventoryRepository.save(inventory);
    }

    @Deprecated
    public Optional<InventoryItem> findByProduct(Product product) {
        return inventoryRepository.findByProduct(product);
    }

    @Deprecated
    public Optional<InventoryItem> findByProductId(Long productId) {
        return inventoryRepository.findByProductIdWithLock(productId);
    }

    @Deprecated
    @Transactional
    public void addStock(Long productId, Integer quantity) {
        InventoryItem inventory = inventoryRepository.findByProductIdWithLock(productId)
                .orElseThrow(() -> new RuntimeException("Inventory not found for product: " + productId));

        inventory.incrementQuantity(quantity);
        inventoryRepository.save(inventory);
        broadcastInventoryUpdate(productId, inventory.getQuantity());
        log.info("Added {} units to product {}", quantity, productId);
    }

    @Deprecated
    @Transactional
    public void removeStock(Long productId, Integer quantity) {
        InventoryItem inventory = inventoryRepository.findByProductIdWithLock(productId)
                .orElseThrow(() -> new RuntimeException("Inventory not found for product: " + productId));

        inventory.decrementQuantity(quantity);
        inventoryRepository.save(inventory);
        broadcastInventoryUpdate(productId, inventory.getQuantity());
        log.info("Removed {} units from product {}", quantity, productId);
    }

    @Deprecated
    @Transactional
    public void reserveInventory(Long productId, Integer quantity) {
        InventoryItem inventory = inventoryRepository.findByProductIdWithLock(productId)
                .orElseThrow(() -> new RuntimeException("Inventory not found for product: " + productId));

        if (inventory.getAvailableQuantity() < quantity) {
            throw new RuntimeException("Insufficient available inventory");
        }

        inventory.setReservedQuantity(inventory.getReservedQuantity() + quantity);
        inventoryRepository.save(inventory);
        broadcastInventoryUpdate(productId, inventory.getQuantity());
    }

    @Deprecated
    @Transactional
    public void releaseReservation(Long productId, Integer quantity) {
        InventoryItem inventory = inventoryRepository.findByProductIdWithLock(productId)
                .orElseThrow(() -> new RuntimeException("Inventory not found for product: " + productId));

        inventory.setReservedQuantity(Math.max(0, inventory.getReservedQuantity() - quantity));
        inventoryRepository.save(inventory);
        broadcastInventoryUpdate(productId, inventory.getQuantity());
    }

    @Deprecated
    public boolean isInStock(Long productId, Integer quantity) {
        return inventoryRepository.findByProductIdWithLock(productId)
                .map(inventory -> inventory.getAvailableQuantity() >= quantity)
                .orElse(false);
    }

    @Deprecated
    public List<InventoryItem> findLowStockItems() {
        return inventoryRepository.findItemsNeedingReorder();
    }

    /** @deprecated use ShopInventoryService.calculateTotalInventoryValue() - this sums the
     * deprecated InventoryItem pool, which is never populated in normal operation. */
    @Deprecated
    public BigDecimal calculateTotalInventoryValue() {
        return inventoryRepository.calculateTotalInventoryValue();
    }

    @Deprecated
    public void updateReorderLevel(Long productId, Integer newLevel) {
        InventoryItem inventory = inventoryRepository.findByProductIdWithLock(productId)
                .orElseThrow(() -> new RuntimeException("Inventory not found for product: " + productId));

        inventory.setReorderLevel(newLevel);
        inventoryRepository.save(inventory);
    }

    @Deprecated
    public Map<String, Integer> getChannelInventory(Long productId) {
        InventoryItem item = inventoryRepository.findByProductIdWithLock(productId)
                .orElseThrow(() -> new RuntimeException("Inventory not found"));

        return Map.of(
                "total", item.getQuantity(),
                "available", item.getAvailableQuantity(),
                "reserved", item.getReservedQuantity()
        );
    }

    // ==================== Stock Reporting Methods ====================

    /**
     * Generate a global stock summary report across all shops.
     * Provides high-level metrics: total products, stock units, value, per-shop breakdown.
     */
    @Transactional(readOnly = true)
    public StockSummaryReport generateStockSummaryReport() {
        log.info("Generating global stock summary report");

        List<InventoryTotal> allInventory = inventoryTotalRepository.findAllWithShopAndProduct();
        List<Shop> activeShops = shopRepository.findByActiveTrue();
        int outOfStockCount = inventoryTotalRepository.findOutOfStockItems().size();

        // Build per-shop breakdown
        Map<Long, List<InventoryTotal>> byShop = allInventory.stream()
                .collect(Collectors.groupingBy(it -> it.getShop().getId()));

        List<StockSummaryReport.ShopStockSummary> shopBreakdown = new ArrayList<>();
        for (Map.Entry<Long, List<InventoryTotal>> entry : byShop.entrySet()) {
            List<InventoryTotal> shopItems = entry.getValue();
            Shop shop = shopItems.get(0).getShop();

            int shopTotalUnits = shopItems.stream().mapToInt(InventoryTotal::getTotalstock).sum();
            BigDecimal shopValue = calculateShopStockValue(shop, shopItems);
            int productCount = (int) shopItems.stream()
                    .filter(it -> it.getTotalstock() > 0)
                    .count();

            shopBreakdown.add(StockSummaryReport.ShopStockSummary.builder()
                    .shopId(shop.getId())
                    .shopName(shop.getName())
                    .shopCode(shop.getCode())
                    .shopType(shop.getType().name())
                    .productCount(productCount)
                    .totalStockUnits(shopTotalUnits)
                    .totalStockValue(shopValue)
                    .build());
        }

        // Count low stock items by checking against reorder levels from ShopInventory
        int lowStockCount = countLowStockItems(allInventory);

        int totalUnits = allInventory.stream().mapToInt(InventoryTotal::getTotalstock).sum();
        BigDecimal totalValue = shopBreakdown.stream()
                .map(StockSummaryReport.ShopStockSummary::getTotalStockValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int totalProducts = (int) allInventory.stream()
                .map(it -> it.getProduct().getId())
                .distinct()
                .count();

        StockSummaryReport report = StockSummaryReport.builder()
                .totalProducts(totalProducts)
                .totalStockUnits(totalUnits)
                .totalStockValue(totalValue)
                .activeShopCount(activeShops.size())
                .lowStockItemCount(lowStockCount)
                .outOfStockItemCount(outOfStockCount)
                .shopBreakdown(shopBreakdown)
                .generatedAt(LocalDateTime.now())
                .build();

        log.info("Stock summary report generated: {} products, {} units, {} value across {} shops",
                totalProducts, totalUnits, totalValue, activeShops.size());

        return report;
    }

    /**
     * Generate a transfer activity report for a given date range.
     * Summarizes all transfers with status breakdowns, values, and individual transfer details.
     */
    @Transactional(readOnly = true)
    public TransferReport generateTransferReport(LocalDateTime startDate, LocalDateTime endDate) {
        log.info("Generating transfer report from {} to {}", startDate, endDate);

        List<InventoryTransfer> transfers;
        if (startDate != null && endDate != null) {
            transfers = transferRepository.findByDateRangeWithShops(startDate, endDate);
        } else {
            transfers = transferRepository.findAllWithShops();
        }

        // Initialize lazy collections for each transfer
        transfers.forEach(t -> {
            t.getTransferItems().size();
            t.getDamagedItems().size();
        });

        // Status breakdown
        Map<String, Long> byStatus = transfers.stream()
                .collect(Collectors.groupingBy(t -> t.getStatus().name(), Collectors.counting()));

        // Type breakdown
        Map<String, Long> byType = transfers.stream()
                .collect(Collectors.groupingBy(t -> t.getTransferType().name(), Collectors.counting()));

        // Value totals
        BigDecimal totalTransferValue = transfers.stream()
                .map(InventoryTransfer::getTotalValue)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalReceivedValue = transfers.stream()
                .map(InventoryTransfer::getTotalReceivedValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDamageValue = transfers.stream()
                .map(InventoryTransfer::getTotalDamageValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int overdueCount = (int) transfers.stream().filter(InventoryTransfer::isOverdue).count();

        // Build individual transfer items
        List<TransferReport.TransferReportItem> items = transfers.stream()
                .map(t -> TransferReport.TransferReportItem.builder()
                        .transferId(t.getId())
                        .transferNumber(t.getTransferNumber())
                        .fromShopName(t.getFromShop().getName())
                        .toShopName(t.getToShop().getName())
                        .status(t.getStatus().name())
                        .transferType(t.getTransferType().name())
                        .priority(t.getPriority().name())
                        .totalItems(t.getTotalItems())
                        .shippedItems(t.getTotalShippedItems())
                        .receivedItems(t.getTotalReceivedItems())
                        .damagedItems(t.getTotalDamagedItems())
                        .totalValue(t.getTotalValue())
                        .requestedAt(t.getRequestedAt())
                        .shippedAt(t.getShippedAt())
                        .receivedAt(t.getReceivedAt())
                        .isOverdue(t.isOverdue())
                        .build())
                .collect(Collectors.toList());

        TransferReport report = TransferReport.builder()
                .totalTransfers(transfers.size())
                .transfersByStatus(byStatus)
                .transfersByType(byType)
                .totalTransferValue(totalTransferValue)
                .totalReceivedValue(totalReceivedValue)
                .totalDamageValue(totalDamageValue)
                .overdueTransferCount(overdueCount)
                .transfers(items)
                .startDate(startDate)
                .endDate(endDate)
                .generatedAt(LocalDateTime.now())
                .build();

        log.info("Transfer report generated: {} transfers, total value: {}, damage value: {}",
                transfers.size(), totalTransferValue, totalDamageValue);

        return report;
    }

    /**
     * Generate a detailed stock report for a specific shop.
     * Lists all products with stock levels, values, and stock status indicators.
     */
    @Transactional(readOnly = true)
    public ShopStockReport generateShopStockReport(Long shopId) {
        log.info("Generating shop stock report for shop ID: {}", shopId);

        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new RuntimeException("Shop not found with id: " + shopId));

        List<InventoryTotal> inventoryTotals = inventoryTotalRepository.findByShopIdWithDetails(shopId);

        int lowStockCount = 0;
        int outOfStockCount = 0;
        BigDecimal totalValue = BigDecimal.ZERO;
        int totalUnits = 0;

        List<ShopStockReport.ProductStockDetail> productDetails = new ArrayList<>();

        for (InventoryTotal it : inventoryTotals) {
            Product product = it.getProduct();

            // Reorder/min/max stock thresholds aren't part of FIFO cost layers - still read
            // from the most recent ShopInventory record for display purposes only.
            Optional<ShopInventory> siOpt = shopInventoryRepository
                    .findFirstByShopAndProductOrderByIdDesc(shop, product);

            Integer reorderLevel = siOpt.map(ShopInventory::getReorderLevel).orElse(null);
            Integer minStock = siOpt.map(ShopInventory::getMinStock).orElse(null);
            Integer maxStock = siOpt.map(ShopInventory::getMaxStock).orElse(null);

            int currentStock = it.getTotalstock();
            // Real FIFO valuation, not the most-recently-received lot's price applied to the
            // whole on-hand quantity - see calculateShopStockValue's comment.
            BigDecimal productValue = inventoryValuationService.getInventoryValue(shop, product);
            BigDecimal unitPrice = inventoryValuationService.getUnitCost(shop, product).orElse(BigDecimal.ZERO);

            // Determine stock status
            String stockStatus;
            if (currentStock <= 0) {
                stockStatus = "OUT_OF_STOCK";
                outOfStockCount++;
            } else if (reorderLevel != null && currentStock <= reorderLevel) {
                stockStatus = "LOW_STOCK";
                lowStockCount++;
            } else {
                stockStatus = "IN_STOCK";
            }

            totalValue = totalValue.add(productValue);
            totalUnits += currentStock;

            productDetails.add(ShopStockReport.ProductStockDetail.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .productBarcode(product.getBarcode())
                    .productSku(product.getSku())
                    .category(product.getCategory())
                    .currentStock(currentStock)
                    .unitPrice(unitPrice)
                    .totalValue(productValue)
                    .reorderLevel(reorderLevel)
                    .minStock(minStock)
                    .maxStock(maxStock)
                    .stockStatus(stockStatus)
                    .build());
        }

        ShopStockReport report = ShopStockReport.builder()
                .shopId(shop.getId())
                .shopName(shop.getName())
                .shopCode(shop.getCode())
                .shopType(shop.getType().name())
                .totalProducts(inventoryTotals.size())
                .totalStockUnits(totalUnits)
                .totalStockValue(totalValue)
                .lowStockProductCount(lowStockCount)
                .outOfStockProductCount(outOfStockCount)
                .products(productDetails)
                .generatedAt(LocalDateTime.now())
                .build();

        log.info("Shop stock report generated for {}: {} products, {} units, {} value",
                shop.getName(), inventoryTotals.size(), totalUnits, totalValue);

        return report;
    }

    /**
     * Generate a global stock value report with breakdowns by shop and category.
     * Provides financial valuation of all inventory across the organization.
     */
    @Transactional(readOnly = true)
    public StockValueReport generateStockValueReport() {
        log.info("Generating global stock value report");

        List<InventoryTotal> allInventory = inventoryTotalRepository.findAllWithShopAndProduct();

        BigDecimal grandTotalValue = BigDecimal.ZERO;
        int grandTotalUnits = 0;
        Set<Long> distinctProducts = new HashSet<>();

        // Per-shop value tracking
        Map<Long, BigDecimal> shopValueMap = new LinkedHashMap<>();
        Map<Long, Integer> shopUnitsMap = new LinkedHashMap<>();
        Map<Long, Integer> shopProductCountMap = new LinkedHashMap<>();
        Map<Long, Shop> shopMap = new LinkedHashMap<>();

        // Per-category value tracking
        Map<String, BigDecimal> categoryValueMap = new LinkedHashMap<>();
        Map<String, Integer> categoryUnitsMap = new LinkedHashMap<>();
        Map<String, Set<Long>> categoryProductsMap = new LinkedHashMap<>();

        for (InventoryTotal it : allInventory) {
            Shop shop = it.getShop();
            Product product = it.getProduct();
            int stock = it.getTotalstock();

            // Real FIFO valuation, not the most-recently-received lot's price applied to the
            // whole on-hand quantity - see calculateShopStockValue's comment.
            BigDecimal lineValue = inventoryValuationService.getInventoryValue(shop, product);

            grandTotalValue = grandTotalValue.add(lineValue);
            grandTotalUnits += stock;
            distinctProducts.add(product.getId());

            // Shop aggregation
            Long sid = shop.getId();
            shopMap.putIfAbsent(sid, shop);
            shopValueMap.merge(sid, lineValue, BigDecimal::add);
            shopUnitsMap.merge(sid, stock, Integer::sum);
            shopProductCountMap.merge(sid, 1, Integer::sum);

            // Category aggregation
            String category = product.getCategory() != null ? product.getCategory() : "Uncategorized";
            categoryValueMap.merge(category, lineValue, BigDecimal::add);
            categoryUnitsMap.merge(category, stock, Integer::sum);
            categoryProductsMap.computeIfAbsent(category, k -> new HashSet<>()).add(product.getId());
        }

        // Build shop value breakdowns
        List<StockValueReport.ShopValueBreakdown> shopValues = new ArrayList<>();
        for (Map.Entry<Long, Shop> entry : shopMap.entrySet()) {
            Long sid = entry.getKey();
            Shop shop = entry.getValue();
            BigDecimal shopValue = shopValueMap.getOrDefault(sid, BigDecimal.ZERO);
            BigDecimal pct = grandTotalValue.compareTo(BigDecimal.ZERO) > 0
                    ? shopValue.multiply(BigDecimal.valueOf(100)).divide(grandTotalValue, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            shopValues.add(StockValueReport.ShopValueBreakdown.builder()
                    .shopId(sid)
                    .shopName(shop.getName())
                    .shopCode(shop.getCode())
                    .shopType(shop.getType().name())
                    .totalValue(shopValue)
                    .totalUnits(shopUnitsMap.getOrDefault(sid, 0))
                    .productCount(shopProductCountMap.getOrDefault(sid, 0))
                    .percentageOfTotal(pct)
                    .build());
        }

        // Build category value breakdowns
        List<StockValueReport.CategoryValueBreakdown> categoryValues = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : categoryValueMap.entrySet()) {
            String category = entry.getKey();
            BigDecimal catValue = entry.getValue();
            BigDecimal pct = grandTotalValue.compareTo(BigDecimal.ZERO) > 0
                    ? catValue.multiply(BigDecimal.valueOf(100)).divide(grandTotalValue, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            categoryValues.add(StockValueReport.CategoryValueBreakdown.builder()
                    .category(category)
                    .totalValue(catValue)
                    .totalUnits(categoryUnitsMap.getOrDefault(category, 0))
                    .productCount(categoryProductsMap.getOrDefault(category, Collections.emptySet()).size())
                    .percentageOfTotal(pct)
                    .build());
        }

        StockValueReport report = StockValueReport.builder()
                .totalInventoryValue(grandTotalValue)
                .totalStockUnits(grandTotalUnits)
                .totalProducts(distinctProducts.size())
                .shopValues(shopValues)
                .categoryValues(categoryValues)
                .generatedAt(LocalDateTime.now())
                .build();

        log.info("Stock value report generated: total value = {}, {} units, {} products",
                grandTotalValue, grandTotalUnits, distinctProducts.size());

        return report;
    }

    // ==================== Private Helpers ====================

    /**
     * Real FIFO cost-layer value of a shop's on-hand stock - delegates to
     * InventoryValuationService rather than pricing every unit at the most recently received
     * lot's cost (the approximation this reporting section used before, which understates or
     * overstates value whenever a product has multiple cost layers at different prices; see
     * INVENTORY_VALUATION.md).
     */
    private BigDecimal calculateShopStockValue(Shop shop, List<InventoryTotal> shopItems) {
        BigDecimal total = BigDecimal.ZERO;
        for (InventoryTotal it : shopItems) {
            total = total.add(inventoryValuationService.getInventoryValue(shop, it.getProduct()));
        }
        return total;
    }

    private int countLowStockItems(List<InventoryTotal> allInventory) {
        int count = 0;
        for (InventoryTotal it : allInventory) {
            if (it.getTotalstock() <= 0) continue;
            Optional<ShopInventory> siOpt = shopInventoryRepository
                    .findFirstByShopAndProductOrderByIdDesc(it.getShop(), it.getProduct());
            if (siOpt.isPresent()) {
                Integer reorderLevel = siOpt.get().getReorderLevel();
                if (reorderLevel != null && it.getTotalstock() <= reorderLevel) {
                    count++;
                }
            }
        }
        return count;
    }

    private void broadcastInventoryUpdate(Long productId, Integer newQuantity) {
        Map<String, Object> update = Map.of(
                "productId", productId,
                "quantity", newQuantity,
                "timestamp", LocalDateTime.now()
        );
        messagingTemplate.convertAndSend("/topic/inventory", update);
    }
}
