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

import org.hibernate.Hibernate;

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
     * Initialize lazy-loaded collections so they are available after the transaction closes.
     */
    private void initializeCollections(InventoryTransfer transfer) {
        Hibernate.initialize(transfer.getTransferItems());
        Hibernate.initialize(transfer.getDamagedItems());
    }

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
        initializeCollections(savedTransfer);
        log.info("Created inventory transfer {} from shop {} to shop {}",
                savedTransfer.getTransferNumber(), fromShop.getName(), toShop.getName());

        return savedTransfer;
    }

    /**
     * Add items to transfer - supports multiple products
     */
    public InventoryTransfer addItemToTransfer(Long transferId, List<Long> productIds, Integer quantity,
                                               BigDecimal unitCost, String notes) {

        InventoryTransfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer", transferId));

        if (transfer.getStatus() != TransferStatus.PENDING) {
            throw new IllegalStateException("Cannot add items to transfer in status: " + transfer.getStatus());
        }

        // Validate quantity
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }

        // Validate product IDs list
        if (productIds == null || productIds.isEmpty()) {
            throw new IllegalArgumentException("Product IDs list cannot be empty");
        }

        // Track successfully added products
        List<String> addedProducts = new ArrayList<>();

        // Add each product to the transfer
        for (Long productId : productIds) {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product", productId));

            // Check if item already exists in transfer
            boolean itemExists = transfer.getTransferItems().stream()
                    .anyMatch(item -> item.getProduct().getId().equals(productId));

            if (itemExists) {
                throw new IllegalArgumentException("Product " + product.getName() + " already exists in this transfer");
            }

            // Check if source shop has sufficient inventory
            if (!shopInventoryService.isInStock(transfer.getFromShop().getId(), productId, quantity)) {
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

            InventoryTransferItem transferItem = InventoryTransferItem.builder()
                    .product(product)
                    .requestedQuantity(quantity)
                    .unitCost(unitCost)
                    .notes(notes)
                    .build();

            transfer.addTransferItem(transferItem);
            addedProducts.add(product.getName());

            log.debug("Added product {} (quantity: {}) to transfer {}",
                    product.getName(),
                    quantity,
                    transfer.getTransferNumber());
        }

        InventoryTransfer savedTransfer = transferRepository.save(transfer);
        initializeCollections(savedTransfer);
        log.info("Added {} product(s) to transfer {}: {} - Total items: {}, Total value: {}",
                addedProducts.size(),
                transfer.getTransferNumber(),
                String.join(", ", addedProducts),
                transfer.getTotalItems(),
                transfer.getTotalValue());

        return savedTransfer;
    }

    /**
     * Remove item from transfer
     */
    public InventoryTransfer removeItemFromTransfer(Long transferId, Long productId) {
        InventoryTransfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer", transferId));

        if (transfer.getStatus() != TransferStatus.PENDING) {
            throw new IllegalStateException("Cannot remove items from transfer in status: " + transfer.getStatus());
        }

        transfer.getTransferItems().removeIf(item -> item.getProduct().getId().equals(productId));

        InventoryTransfer savedTransfer = transferRepository.save(transfer);
        initializeCollections(savedTransfer);
        return savedTransfer;
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
            if (!shopInventoryService.isInStock(
                    transfer.getFromShop().getId(),
                    item.getProduct().getId(),
                    item.getRequestedQuantity())) {
                throw new InsufficientInventoryException("Insufficient inventory for product: " + item.getProduct().getName());
            }
        }

        transfer.approve(approver);

        InventoryTransfer savedTransfer = transferRepository.save(transfer);
        initializeCollections(savedTransfer);
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
            if (!shopInventoryService.isInStock(
                    transfer.getFromShop().getId(),
                    item.getProduct().getId(),
                    item.getRequestedQuantity())) {
                throw new InsufficientInventoryException("Insufficient inventory for product: " + item.getProduct().getName());
            }
        }

        transfer.ship(shipper);

        // Update inventory in both shops
        for (InventoryTransferItem item : transfer.getTransferItems()) {
            try {
                // 1. Remove stock from source shop
                shopInventoryService.reduceStock(
                        transfer.getFromShop().getId(),
                        item.getProduct().getId(),
                        item.getRequestedQuantity());


                // 2. Mark as shipped in transfer item
                item.setShippedQuantity(item.getRequestedQuantity());

                log.debug("Shipped {} units of {} from shop {} to shop {}",
                        item.getRequestedQuantity(),
                        item.getProduct().getName(),
                        transfer.getFromShop().getName(),
                        transfer.getToShop().getName());

            } catch (Exception e) {
                log.error("Error updating inventory during shipping for product {}: {}",
                        item.getProduct().getName(), e.getMessage());
                throw new RuntimeException("Failed to update inventory during shipping", e);
            }
        }

        InventoryTransfer savedTransfer = transferRepository.save(transfer);
        initializeCollections(savedTransfer);
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
                    .filter(item -> item.getProduct().getId().equals(receivedItem.getProductId()))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found in transfer with id: " + receivedItem.getProductId()));

            // Validate received quantities
            int totalReceived = receivedItem.getReceivedQuantity() +
                    (receivedItem.getDamagedQuantity() != null ? receivedItem.getDamagedQuantity() : 0);

            if (totalReceived > transferItem.getShippedQuantity()) {
                throw new IllegalArgumentException(
                        String.format("Cannot receive more than shipped quantity for product %s. Shipped: %d, Attempting to receive: %d",
                                transferItem.getProduct().getName(),
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
                            transferItem.getProduct().getId(),
                            receivedItem.getReceivedQuantity());

                    log.debug("Added {} units of {} to shop {} inventory",
                            receivedItem.getReceivedQuantity(),
                            transferItem.getProduct().getName(),
                            transfer.getToShop().getName());
                }

                // 2. Log damaged items (these are not added to inventory)
                if (receivedItem.getDamagedQuantity() != null && receivedItem.getDamagedQuantity() > 0) {
                    log.warn("Received {} damaged units of {} in transfer {} - not added to inventory",
                            receivedItem.getDamagedQuantity(),
                            transferItem.getProduct().getName(),
                            transfer.getTransferNumber());
                }

                // 3. Handle partial receipts (items not received at all)
                int unreceivedQuantity = transferItem.getShippedQuantity() - totalReceived;
                if (unreceivedQuantity > 0) {
                    log.warn("Missing {} units of {} in transfer {} - possible loss or theft",
                            unreceivedQuantity,
                            transferItem.getProduct().getName(),
                            transfer.getTransferNumber());
                }

            } catch (Exception e) {
                log.error("Error updating inventory during receiving for product {}: {}",
                        transferItem.getProduct().getName(), e.getMessage());
                throw new RuntimeException("Failed to update inventory during receiving", e);
            }
        }

        transfer.receive(receiver);

        InventoryTransfer savedTransfer = transferRepository.save(transfer);
        initializeCollections(savedTransfer);

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
                try {
                    // 1. Return stock to source shop (add back what was removed)
                    shopInventoryService.addStock(
                            transfer.getFromShop().getId(),
                            item.getProduct().getId(),
                            item.getShippedQuantity());

                    log.debug("Reversed inventory for product {} - returned {} units to {}",
                            item.getProduct().getName(),
                            item.getShippedQuantity(),
                            transfer.getFromShop().getName());

                } catch (Exception e) {
                    log.error("Error reversing inventory during cancellation for product {}: {}",
                            item.getProduct().getName(), e.getMessage());
                    // Log error but don't fail the entire cancellation
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
        initializeCollections(savedTransfer);
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
        initializeCollections(savedTransfer);
        log.info("Completed transfer {}", transfer.getTransferNumber());

        return savedTransfer;
    }

    // Query methods

    @Transactional(readOnly = true)
    public Optional<InventoryTransfer> findById(Long transferId) {
        Optional<InventoryTransfer> transfer = transferRepository.findById(transferId);
        transfer.ifPresent(this::initializeCollections);
        return transfer;
    }

    @Transactional(readOnly = true)
    public Optional<InventoryTransfer> findByTransferNumber(String transferNumber) {
        Optional<InventoryTransfer> transfer = transferRepository.findByTransferNumber(transferNumber);
        transfer.ifPresent(this::initializeCollections);
        return transfer;
    }

    @Transactional(readOnly = true)
    public Page<InventoryTransfer> findTransfersForShop(Long shopId, Pageable pageable) {
        Page<InventoryTransfer> page = transferRepository.findByShopInvolved(shopId, pageable);
        page.getContent().forEach(this::initializeCollections);
        return page;
    }

    @Transactional(readOnly = true)
    public Page<InventoryTransfer> findTransfersFromShop(Long shopId, Pageable pageable) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop", shopId));
        Page<InventoryTransfer> page = transferRepository.findByFromShop(shop, pageable);
        page.getContent().forEach(this::initializeCollections);
        return page;
    }

    @Transactional(readOnly = true)
    public Page<InventoryTransfer> findTransfersToShop(Long shopId, Pageable pageable) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop", shopId));
        Page<InventoryTransfer> page = transferRepository.findByToShop(shop, pageable);
        page.getContent().forEach(this::initializeCollections);
        return page;
    }

    @Transactional(readOnly = true)
    public List<InventoryTransfer> findByStatus(TransferStatus status) {
        List<InventoryTransfer> transfers = transferRepository.findByStatus(status);
        transfers.forEach(this::initializeCollections);
        return transfers;
    }

    @Transactional(readOnly = true)
    public List<InventoryTransfer> findOverdueTransfers() {
        List<InventoryTransfer> transfers = transferRepository.findOverdueTransfers(LocalDateTime.now());
        transfers.forEach(this::initializeCollections);
        return transfers;
    }

    @Transactional(readOnly = true)
    public List<InventoryTransfer> findByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        List<InventoryTransfer> transfers = transferRepository.findByDateRange(startDate, endDate);
        transfers.forEach(this::initializeCollections);
        return transfers;
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