package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.cashier.Cashier;
import com.pos_onlineshop.hybrid.enums.TransferPriority;
import com.pos_onlineshop.hybrid.enums.TransferStatus;
import com.pos_onlineshop.hybrid.enums.TransferType;
import com.pos_onlineshop.hybrid.exceptions.InsufficientInventoryException;
import com.pos_onlineshop.hybrid.exceptions.ResourceNotFoundException;
import com.pos_onlineshop.hybrid.inventoryTransfer.InventoryTransfer;
import com.pos_onlineshop.hybrid.inventoryTransfer.InventoryTransferRepository;
import com.pos_onlineshop.hybrid.inventoryTransferItems.InventoryTransferItem;
import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.products.ProductRepository;
import com.pos_onlineshop.hybrid.shop.Shop;
import com.pos_onlineshop.hybrid.shop.ShopRepository;
import com.pos_onlineshop.hybrid.shopInventory.ShopInventory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class InventoryTransferService {

    private final InventoryTransferRepository transferRepository;
    private final ShopRepository shopRepository;
    private final ProductRepository productRepository;
    private final ShopInventoryService shopInventoryService;

    /**
     * Create a new inventory transfer request
     */
    public InventoryTransfer createTransfer(Long fromShopId, Long toShopId, Cashier initiator,
                                            TransferType transferType, TransferPriority priority,
                                            String notes) {

        Shop fromShop = shopRepository.findById(fromShopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop", fromShopId));

        Shop toShop = shopRepository.findById(toShopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop", toShopId));

        if (fromShop.equals(toShop)) {
            throw new IllegalArgumentException("Source and destination shops cannot be the same");
        }

        InventoryTransfer transfer = InventoryTransfer.builder()
                .fromShop(fromShop)
                .toShop(toShop)
                .transferNumber(generateTransferNumber())
                .initiatedBy(initiator)
                .transferType(transferType != null ? transferType : TransferType.REPLENISHMENT)
                .priority(priority != null ? priority : TransferPriority.NORMAL)
                .notes(notes)
                .status(TransferStatus.PENDING)
                .build();

        InventoryTransfer savedTransfer = transferRepository.save(transfer);
        log.info("Created inventory transfer {} from shop {} to shop {}",
                savedTransfer.getTransferNumber(), fromShop.getName(), toShop.getName());

        return savedTransfer;
    }

    /**
     * Add item to transfer with multiple products
     */
    public InventoryTransfer addItemToTransfer(Long transferId, List<Long> productIds, Integer quantity,
                                               BigDecimal unitCost, String notes) {

        InventoryTransfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer", transferId));

        if (transfer.getStatus() != TransferStatus.PENDING) {
            throw new IllegalStateException("Cannot add items to transfer in status: " + transfer.getStatus());
        }

        // Validate product IDs
        if (productIds == null || productIds.isEmpty()) {
            throw new IllegalArgumentException("Product IDs cannot be empty");
        }

        // Validate quantity
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }

        // Fetch all products
        List<Product> products = new ArrayList<>();
        for (Long productId : productIds) {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
            products.add(product);
        }

        // Check if any product already exists in transfer
        for (Product product : products) {
            boolean itemExists = transfer.getTransferItems().stream()
                    .anyMatch(item -> item.getProducts().stream()
                            .anyMatch(p -> p.getId().equals(product.getId())));

            if (itemExists) {
                throw new IllegalArgumentException("Product " + product.getName() + " already exists in this transfer");
            }
        }

        // Check if source shop has sufficient inventory for all products
        for (Product product : products) {
            if (!shopInventoryService.isInStock(transfer.getFromShop().getId(), product.getId(), quantity)) {
                // Get actual available quantity for better error message
                Optional<ShopInventory> inventory = shopInventoryService.getInventory(transfer.getFromShop(), product);
                int availableQuantity = inventory.map(ShopInventory::getQuantity).orElse(0);

                throw new InsufficientInventoryException(
                        String.format("Insufficient inventory in source shop %s. Requested: %d, Available: %d for product %s",
                                transfer.getFromShop().getName(),
                                quantity,
                                availableQuantity,
                                product.getName()));
            }
        }

        InventoryTransferItem transferItem = InventoryTransferItem.builder()
                .products(products)
                .requestedQuantity(quantity)
                .unitCost(unitCost)
                .notes(notes)
                .build();

        transfer.addTransferItem(transferItem);

        InventoryTransfer savedTransfer = transferRepository.save(transfer);

        String productNames = products.stream()
                .map(Product::getName)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");

        log.info("Added products {} (quantity: {} each) to transfer {} - Total items: {}, Total value: {}",
                productNames,
                quantity,
                transfer.getTransferNumber(),
                transfer.getTotalItems(),
                transfer.getTotalValue());

        return savedTransfer;
    }

    /**
     * Remove item from transfer by item ID
     */
    public InventoryTransfer removeItemFromTransfer(Long transferId, Long itemId) {
        InventoryTransfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer", transferId));

        if (transfer.getStatus() != TransferStatus.PENDING) {
            throw new IllegalStateException("Cannot remove items from transfer in status: " + transfer.getStatus());
        }

        transfer.getTransferItems().removeIf(item -> item.getId().equals(itemId));

        return transferRepository.save(transfer);
    }

    /**
     * Approve transfer
     */
    public InventoryTransfer approveTransfer(Long transferId, Cashier approver) {
        InventoryTransfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer", transferId));

        if (transfer.getTransferItems().isEmpty()) {
            throw new IllegalStateException("Cannot approve transfer with no items");
        }

        // Validate inventory availability again
        for (InventoryTransferItem item : transfer.getTransferItems()) {
            for (Product product : item.getProducts()) {
                if (!shopInventoryService.isInStock(
                        transfer.getFromShop().getId(),
                        product.getId(),
                        item.getRequestedQuantity())) {
                    throw new InsufficientInventoryException("Insufficient inventory for product: " + product.getName());
                }
            }
        }

        transfer.approve(approver);

        InventoryTransfer savedTransfer = transferRepository.save(transfer);
        log.info("Approved transfer {} by {}", transfer.getTransferNumber(), approver.getUsername());

        return savedTransfer;
    }

    /**
     * Ship transfer - updates inventory and sets status to IN_TRANSIT
     */
    public InventoryTransfer shipTransfer(Long transferId, Cashier shipper) {
        InventoryTransfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer", transferId));

        // Validate inventory availability before shipping
        for (InventoryTransferItem item : transfer.getTransferItems()) {
            for (Product product : item.getProducts()) {
                if (!shopInventoryService.isInStock(
                        transfer.getFromShop().getId(),
                        product.getId(),
                        item.getRequestedQuantity())) {
                    throw new InsufficientInventoryException("Insufficient inventory for product: " + product.getName());
                }
            }
        }

        transfer.ship(shipper);

        // Update inventory in both shops
        for (InventoryTransferItem item : transfer.getTransferItems()) {
            for (Product product : item.getProducts()) {
                try {
                    // 1. Remove stock from source shop
                    shopInventoryService.reduceStock(
                            transfer.getFromShop().getId(),
                            product.getId(),
                            item.getRequestedQuantity());

                    // 2. Mark as shipped in transfer item
                    item.setShippedQuantity(item.getRequestedQuantity());

                    log.debug("Shipped {} units of {} from shop {} to shop {}",
                            item.getRequestedQuantity(),
                            product.getName(),
                            transfer.getFromShop().getName(),
                            transfer.getToShop().getName());

                } catch (Exception e) {
                    log.error("Error updating inventory during shipping for product {}: {}",
                            product.getName(), e.getMessage());
                    throw new RuntimeException("Failed to update inventory during shipping", e);
                }
            }
        }

        InventoryTransfer savedTransfer = transferRepository.save(transfer);
        log.info("Shipped transfer {} by {} - {} items from {} to {}",
                transfer.getTransferNumber(),
                shipper.getUsername(),
                transfer.getTotalItems(),
                transfer.getFromShop().getName(),
                transfer.getToShop().getName());

        return savedTransfer;
    }

    /**
     * Receive transfer - updates destination inventory
     */
    public InventoryTransfer receiveTransfer(Long transferId, Cashier receiver,
                                             List<ReceiveItemDto> receivedItems) {

        InventoryTransfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer", transferId));

        // Update received quantities for each item
        for (ReceiveItemDto receivedItem : receivedItems) {
            InventoryTransferItem transferItem = transfer.getTransferItems().stream()
                    .filter(item -> item.getProducts().stream()
                            .anyMatch(p -> p.getId().equals(receivedItem.getProductId())))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found in transfer with id: " + receivedItem.getProductId()));

            // Find the specific product in the item
            Product product = transferItem.getProducts().stream()
                    .filter(p -> p.getId().equals(receivedItem.getProductId()))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + receivedItem.getProductId()));

            // Validate received quantities
            int totalReceived = receivedItem.getReceivedQuantity() +
                    (receivedItem.getDamagedQuantity() != null ? receivedItem.getDamagedQuantity() : 0);

            if (totalReceived > transferItem.getShippedQuantity()) {
                throw new IllegalArgumentException(
                        String.format("Cannot receive more than shipped quantity for product %s. Shipped: %d, Attempting to receive: %d",
                                product.getName(),
                                transferItem.getShippedQuantity(),
                                totalReceived));
            }

            // Update received and damaged quantities in transfer item
            transferItem.receiveQuantity(receivedItem.getReceivedQuantity(), receivedItem.getDamagedQuantity());

            try {
                // 1. Add received stock to destination shop inventory
                if (receivedItem.getReceivedQuantity() > 0) {
                    shopInventoryService.addStock(
                            transfer.getToShop().getId(),
                            product.getId(),
                            receivedItem.getReceivedQuantity());

                    log.debug("Added {} units of {} to shop {} inventory",
                            receivedItem.getReceivedQuantity(),
                            product.getName(),
                            transfer.getToShop().getName());
                }

                // 2. Log damaged items (these are not added to inventory)
                if (receivedItem.getDamagedQuantity() != null && receivedItem.getDamagedQuantity() > 0) {
                    log.warn("Received {} damaged units of {} in transfer {} - not added to inventory",
                            receivedItem.getDamagedQuantity(),
                            product.getName(),
                            transfer.getTransferNumber());
                }

                // 3. Handle partial receipts (items not received at all)
                int unreceivedQuantity = transferItem.getShippedQuantity() - totalReceived;
                if (unreceivedQuantity > 0) {
                    log.warn("Missing {} units of {} in transfer {} - possible loss or theft",
                            unreceivedQuantity,
                            product.getName(),
                            transfer.getTransferNumber());
                }

            } catch (Exception e) {
                log.error("Error updating inventory during receiving for product {}: {}",
                        product.getName(), e.getMessage());
                throw new RuntimeException("Failed to update inventory during receiving", e);
            }
        }

        transfer.receive(receiver);

        InventoryTransfer savedTransfer = transferRepository.save(transfer);

        // Log comprehensive receive summary
        int totalReceived = receivedItems.stream()
                .mapToInt(ReceiveItemDto::getReceivedQuantity)
                .sum();
        int totalDamaged = receivedItems.stream()
                .mapToInt(item -> item.getDamagedQuantity() != null ? item.getDamagedQuantity() : 0)
                .sum();

        log.info("Received transfer {} by {} - Received: {}, Damaged: {}, Total Value: {}",
                transfer.getTransferNumber(),
                receiver.getUsername(),
                totalReceived,
                totalDamaged,
                transfer.getTotalReceivedValue());

        return savedTransfer;
    }

    /**
     * Cancel transfer - reverses inventory changes if transfer was shipped
     */
    public InventoryTransfer cancelTransfer(Long transferId, String reason) {
        InventoryTransfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer", transferId));

        // If transfer was shipped, need to reverse inventory changes
        if (transfer.getStatus() == TransferStatus.IN_TRANSIT) {
            for (InventoryTransferItem item : transfer.getTransferItems()) {
                for (Product product : item.getProducts()) {
                    try {
                        // 1. Return stock to source shop (add back what was removed)
                        shopInventoryService.addStock(
                                transfer.getFromShop().getId(),
                                product.getId(),
                                item.getShippedQuantity());

                        log.debug("Reversed inventory for product {} - returned {} units to {}",
                                product.getName(),
                                item.getShippedQuantity(),
                                transfer.getFromShop().getName());

                    } catch (Exception e) {
                        log.error("Error reversing inventory during cancellation for product {}: {}",
                                product.getName(), e.getMessage());
                        // Log error but don't fail the entire cancellation
                    }
                }
            }

            log.info("Reversed inventory changes for cancelled transfer {}", transfer.getTransferNumber());
        }

        // If transfer was only approved but not shipped, no inventory changes to reverse
        else if (transfer.getStatus() == TransferStatus.APPROVED) {
            log.info("Cancelled approved transfer {} - no inventory changes to reverse", transfer.getTransferNumber());
        }

        transfer.cancel(reason);

        InventoryTransfer savedTransfer = transferRepository.save(transfer);
        log.info("Cancelled transfer {} with reason: {}", transfer.getTransferNumber(), reason);

        return savedTransfer;
    }

    /**
     * Complete transfer
     */
    public InventoryTransfer completeTransfer(Long transferId) {
        InventoryTransfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer", transferId));

        transfer.complete();

        InventoryTransfer savedTransfer = transferRepository.save(transfer);
        log.info("Completed transfer {}", transfer.getTransferNumber());

        return savedTransfer;
    }

    // Query methods

    @Transactional(readOnly = true)
    public Optional<InventoryTransfer> findById(Long transferId) {
        return transferRepository.findById(transferId);
    }

    @Transactional(readOnly = true)
    public Optional<InventoryTransfer> findByTransferNumber(String transferNumber) {
        return transferRepository.findByTransferNumber(transferNumber);
    }

    @Transactional(readOnly = true)
    public Page<InventoryTransfer> findTransfersForShop(Long shopId, Pageable pageable) {
        return transferRepository.findByShopInvolved(shopId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<InventoryTransfer> findTransfersFromShop(Long shopId, Pageable pageable) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop", shopId));
        return transferRepository.findByFromShop(shop, pageable);
    }

    @Transactional(readOnly = true)
    public Page<InventoryTransfer> findTransfersToShop(Long shopId, Pageable pageable) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop", shopId));
        return transferRepository.findByToShop(shop, pageable);
    }

    @Transactional(readOnly = true)
    public List<InventoryTransfer> findByStatus(TransferStatus status) {
        return transferRepository.findByStatus(status);
    }

    @Transactional(readOnly = true)
    public List<InventoryTransfer> findOverdueTransfers() {
        return transferRepository.findOverdueTransfers(LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<InventoryTransfer> findByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return transferRepository.findByDateRange(startDate, endDate);
    }

    @Transactional(readOnly = true)
    public long countActiveTransfersFromShop(Long shopId) {
        return transferRepository.countActiveTransfersFromShop(shopId);
    }

    /**
     * Generate unique transfer number
     */
    private String generateTransferNumber() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String prefix = "TRF";
        return prefix + timestamp;
    }

    /**
     * DTO for receiving items
     */
    public static class ReceiveItemDto {
        private Long productId;
        private Integer receivedQuantity;
        private Integer damagedQuantity;

        // Constructors
        public ReceiveItemDto() {}

        public ReceiveItemDto(Long productId, Integer receivedQuantity, Integer damagedQuantity) {
            this.productId = productId;
            this.receivedQuantity = receivedQuantity;
            this.damagedQuantity = damagedQuantity;
        }

        // Getters and setters
        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }

        public Integer getReceivedQuantity() { return receivedQuantity; }
        public void setReceivedQuantity(Integer receivedQuantity) { this.receivedQuantity = receivedQuantity; }

        public Integer getDamagedQuantity() { return damagedQuantity; }
        public void setDamagedQuantity(Integer damagedQuantity) { this.damagedQuantity = damagedQuantity; }
    }
}