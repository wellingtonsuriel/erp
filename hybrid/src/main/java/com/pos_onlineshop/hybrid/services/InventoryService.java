package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.inventory.InventoryItem;
import com.pos_onlineshop.hybrid.inventory.InventoryItemRepository;
import com.pos_onlineshop.hybrid.products.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class InventoryService {

    private final InventoryItemRepository inventoryRepository;
    private final SimpMessagingTemplate messagingTemplate;

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

    public Optional<InventoryItem> findByProduct(Product product) {
        return inventoryRepository.findByProduct(product);
    }

    public Optional<InventoryItem> findByProductId(Long productId) {
        return inventoryRepository.findByProductIdWithLock(productId);
    }

    @Transactional
    public void addStock(Long productId, Integer quantity) {
        InventoryItem inventory = inventoryRepository.findByProductIdWithLock(productId)
                .orElseThrow(() -> new RuntimeException("Inventory not found for product: " + productId));

        inventory.incrementQuantity(quantity);
        inventoryRepository.save(inventory);
        broadcastInventoryUpdate(productId, inventory.getQuantity());
        log.info("Added {} units to product {}", quantity, productId);
    }

    @Transactional
    public void removeStock(Long productId, Integer quantity) {
        InventoryItem inventory = inventoryRepository.findByProductIdWithLock(productId)
                .orElseThrow(() -> new RuntimeException("Inventory not found for product: " + productId));

        inventory.decrementQuantity(quantity);
        inventoryRepository.save(inventory);
        broadcastInventoryUpdate(productId, inventory.getQuantity());
        log.info("Removed {} units from product {}", quantity, productId);
    }

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

    @Transactional
    public void releaseReservation(Long productId, Integer quantity) {
        InventoryItem inventory = inventoryRepository.findByProductIdWithLock(productId)
                .orElseThrow(() -> new RuntimeException("Inventory not found for product: " + productId));

        inventory.setReservedQuantity(Math.max(0, inventory.getReservedQuantity() - quantity));
        inventoryRepository.save(inventory);
        broadcastInventoryUpdate(productId, inventory.getQuantity());
    }

    public boolean isInStock(Long productId, Integer quantity) {
        return inventoryRepository.findByProductIdWithLock(productId)
                .map(inventory -> inventory.getAvailableQuantity() >= quantity)
                .orElse(false);
    }

    public List<InventoryItem> findLowStockItems() {
        return inventoryRepository.findItemsNeedingReorder();
    }

    public BigDecimal calculateTotalInventoryValue() {
        return inventoryRepository.calculateTotalInventoryValue();
    }

    public void updateReorderLevel(Long productId, Integer newLevel) {
        InventoryItem inventory = inventoryRepository.findByProductIdWithLock(productId)
                .orElseThrow(() -> new RuntimeException("Inventory not found for product: " + productId));

        inventory.setReorderLevel(newLevel);
        inventoryRepository.save(inventory);
    }

    public Map<String, Integer> getChannelInventory(Long productId) {
        InventoryItem item = inventoryRepository.findByProductIdWithLock(productId)
                .orElseThrow(() -> new RuntimeException("Inventory not found"));

        return Map.of(
                "total", item.getQuantity(),
                "available", item.getAvailableQuantity(),
                "reserved", item.getReservedQuantity()
        );
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