package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.cashier.Cashier;
import com.pos_onlineshop.hybrid.cashier.CashierRepository;
import com.pos_onlineshop.hybrid.dtos.*;
import com.pos_onlineshop.hybrid.enums.TransferStatus;
import com.pos_onlineshop.hybrid.exceptions.InsufficientInventoryException;
import com.pos_onlineshop.hybrid.exceptions.ResourceNotFoundException;
import com.pos_onlineshop.hybrid.inventoryTransfer.InventoryTransfer;
import com.pos_onlineshop.hybrid.services.InventoryTransferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/inventory-transfers")
@RequiredArgsConstructor
@Slf4j
public class InventoryTransferController {

    private final InventoryTransferService transferService;
    private final CashierRepository cashierRepository;

    /**
     * Create a new inventory transfer
     */
    @PostMapping
    public ResponseEntity<?> createTransfer(@RequestBody CreateTransferRequest request) {
        try {
            Cashier initiator = cashierRepository.findById(request.getInitiatorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Cashier", request.getInitiatorId()));

            InventoryTransfer transfer = transferService.createTransfer(
                    request.getFromShopId(),
                    request.getToShopId(),
                    initiator,
                    request.getTransferType(),
                    request.getPriority(),
                    request.getNotes()
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(transfer);
        } catch (ResourceNotFoundException e) {
            log.error("Resource not found: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorResponse.of(HttpStatus.NOT_FOUND.value(), "Not Found", e.getMessage(), "/api/inventory-transfers"));
        } catch (IllegalArgumentException e) {
            log.error("Invalid request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), "Bad Request", e.getMessage(), "/api/inventory-transfers"));
        } catch (Exception e) {
            log.error("Error creating transfer", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error", "Failed to create transfer", "/api/inventory-transfers"));
        }
    }

    /**
     * Get transfer by ID
     */
    @GetMapping("/{transferId}")
    public ResponseEntity<InventoryTransfer> getTransfer(@PathVariable Long transferId) {
        Optional<InventoryTransfer> transfer = transferService.findById(transferId);
        return transfer.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get transfer by transfer number
     */
    @GetMapping("/number/{transferNumber}")
    public ResponseEntity<InventoryTransfer> getTransferByNumber(@PathVariable String transferNumber) {
        Optional<InventoryTransfer> transfer = transferService.findByTransferNumber(transferNumber);
        return transfer.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get transfers for a specific shop (both outgoing and incoming)
     */
    @GetMapping("/shop/{shopId}")
    public ResponseEntity<Page<InventoryTransfer>> getTransfersForShop(
            @PathVariable Long shopId,
            Pageable pageable) {

        Page<InventoryTransfer> transfers = transferService.findTransfersForShop(shopId, pageable);
        return ResponseEntity.ok(transfers);
    }

    /**
     * Get outgoing transfers from a shop
     */
    @GetMapping("/shop/{shopId}/outgoing")
    public ResponseEntity<Page<InventoryTransfer>> getOutgoingTransfers(
            @PathVariable Long shopId,
            Pageable pageable) {

        Page<InventoryTransfer> transfers = transferService.findTransfersFromShop(shopId, pageable);
        return ResponseEntity.ok(transfers);
    }

    /**
     * Get incoming transfers to a shop
     */
    @GetMapping("/shop/{shopId}/incoming")
    public ResponseEntity<Page<InventoryTransfer>> getIncomingTransfers(
            @PathVariable Long shopId,
            Pageable pageable) {

        Page<InventoryTransfer> transfers = transferService.findTransfersToShop(shopId, pageable);
        return ResponseEntity.ok(transfers);
    }

    /**
     * Get transfers by status
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<InventoryTransfer>> getTransfersByStatus(@PathVariable TransferStatus status) {
        List<InventoryTransfer> transfers = transferService.findByStatus(status);
        return ResponseEntity.ok(transfers);
    }

    /**
     * Get overdue transfers
     */
    @GetMapping("/overdue")
    public ResponseEntity<List<InventoryTransfer>> getOverdueTransfers() {
        List<InventoryTransfer> transfers = transferService.findOverdueTransfers();
        return ResponseEntity.ok(transfers);
    }

    /**
     * Get transfers by date range
     */
    @GetMapping("/date-range")
    public ResponseEntity<List<InventoryTransfer>> getTransfersByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        List<InventoryTransfer> transfers = transferService.findByDateRange(startDate, endDate);
        return ResponseEntity.ok(transfers);
    }

    /**
     * Add item to transfer (Enhanced - with detailed inventory validation)
     */
    @PostMapping("/{transferId}/items")
    public ResponseEntity<?> addItemToTransfer(
            @PathVariable Long transferId,
            @RequestBody AddItemRequest request) {

        try {
            // Validate request
            if (request.getQuantity() == null || request.getQuantity() <= 0) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), "Bad Request",
                            "Quantity must be greater than zero", "/api/inventory-transfers/" + transferId + "/items"));
            }

            InventoryTransfer transfer = transferService.addItemToTransfer(
                    transferId,
                    request.getProductId(),
                    request.getQuantity(),
                    request.getUnitCost(),
                    request.getNotes()
            );

            log.info("Successfully added item to transfer {} - Product: {}, Quantity: {}, Total items: {}",
                    transfer.getTransferNumber(),
                    request.getProductId(),
                    request.getQuantity(),
                    transfer.getTotalItems());

            return ResponseEntity.ok(transfer);
        } catch (ResourceNotFoundException e) {
            log.error("Resource not found: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorResponse.of(HttpStatus.NOT_FOUND.value(), "Not Found", e.getMessage(),
                        "/api/inventory-transfers/" + transferId + "/items"));
        } catch (InsufficientInventoryException e) {
            log.error("Insufficient inventory: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ErrorResponse.of(HttpStatus.CONFLICT.value(), "Insufficient Inventory", e.getMessage(),
                        "/api/inventory-transfers/" + transferId + "/items"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Invalid operation: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), "Bad Request", e.getMessage(),
                        "/api/inventory-transfers/" + transferId + "/items"));
        } catch (Exception e) {
            log.error("Error adding item to transfer {}: {}", transferId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error",
                        "Failed to add item to transfer", "/api/inventory-transfers/" + transferId + "/items"));
        }
    }

    /**
     * Remove item from transfer
     */
    @DeleteMapping("/{transferId}/items/{productId}")
    public ResponseEntity<?> removeItemFromTransfer(
            @PathVariable Long transferId,
            @PathVariable Long productId) {

        try {
            InventoryTransfer transfer = transferService.removeItemFromTransfer(transferId, productId);
            return ResponseEntity.ok(transfer);
        } catch (ResourceNotFoundException e) {
            log.error("Resource not found: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorResponse.of(HttpStatus.NOT_FOUND.value(), "Not Found", e.getMessage(),
                        "/api/inventory-transfers/" + transferId + "/items/" + productId));
        } catch (IllegalStateException e) {
            log.error("Invalid operation: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), "Bad Request", e.getMessage(),
                        "/api/inventory-transfers/" + transferId + "/items/" + productId));
        } catch (Exception e) {
            log.error("Error removing item from transfer", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error",
                        "Failed to remove item from transfer", "/api/inventory-transfers/" + transferId + "/items/" + productId));
        }
    }

    /**
     * Approve transfer
     */
    @PostMapping("/{transferId}/approve")
    public ResponseEntity<?> approveTransfer(
            @PathVariable Long transferId,
            @RequestBody ApprovalRequest request) {

        try {
            Cashier approver = cashierRepository.findById(request.getApproverId())
                    .orElseThrow(() -> new ResourceNotFoundException("Cashier", request.getApproverId()));

            InventoryTransfer transfer = transferService.approveTransfer(transferId, approver);
            return ResponseEntity.ok(transfer);
        } catch (ResourceNotFoundException e) {
            log.error("Resource not found: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorResponse.of(HttpStatus.NOT_FOUND.value(), "Not Found", e.getMessage(),
                        "/api/inventory-transfers/" + transferId + "/approve"));
        } catch (InsufficientInventoryException e) {
            log.error("Insufficient inventory: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ErrorResponse.of(HttpStatus.CONFLICT.value(), "Insufficient Inventory", e.getMessage(),
                        "/api/inventory-transfers/" + transferId + "/approve"));
        } catch (IllegalStateException e) {
            log.error("Invalid operation: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), "Bad Request", e.getMessage(),
                        "/api/inventory-transfers/" + transferId + "/approve"));
        } catch (Exception e) {
            log.error("Error approving transfer", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error",
                        "Failed to approve transfer", "/api/inventory-transfers/" + transferId + "/approve"));
        }
    }

    /**
     * Ship transfer (Enhanced - removes stock from source, adds in-transit to destination)
     */
    @PostMapping("/{transferId}/ship")
    public ResponseEntity<?> shipTransfer(
            @PathVariable Long transferId,
            @RequestBody ShipmentRequest request) {

        try {
            Cashier shipper = cashierRepository.findById(request.getShipperId())
                    .orElseThrow(() -> new ResourceNotFoundException("Cashier", request.getShipperId()));

            InventoryTransfer transfer = transferService.shipTransfer(transferId, shipper);

            log.info("Successfully shipped transfer {} - {} items from {} to {}",
                    transfer.getTransferNumber(),
                    transfer.getTotalItems(),
                    transfer.getFromShop().getName(),
                    transfer.getToShop().getName());

            return ResponseEntity.ok(transfer);
        } catch (ResourceNotFoundException e) {
            log.error("Resource not found: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorResponse.of(HttpStatus.NOT_FOUND.value(), "Not Found", e.getMessage(),
                        "/api/inventory-transfers/" + transferId + "/ship"));
        } catch (InsufficientInventoryException e) {
            log.error("Insufficient inventory: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ErrorResponse.of(HttpStatus.CONFLICT.value(), "Insufficient Inventory", e.getMessage(),
                        "/api/inventory-transfers/" + transferId + "/ship"));
        } catch (IllegalStateException e) {
            log.error("Invalid operation: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), "Bad Request", e.getMessage(),
                        "/api/inventory-transfers/" + transferId + "/ship"));
        } catch (Exception e) {
            log.error("Error shipping transfer {}: {}", transferId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error",
                        "Failed to ship transfer", "/api/inventory-transfers/" + transferId + "/ship"));
        }
    }

    /**
     * Receive transfer (Enhanced - handles damage tracking and partial receipts)
     */
    @PostMapping("/{transferId}/receive")
    public ResponseEntity<?> receiveTransfer(
            @PathVariable Long transferId,
            @RequestBody ReceiveTransferRequest request) {

        try {
            Cashier receiver = cashierRepository.findById(request.getReceiverId())
                    .orElseThrow(() -> new ResourceNotFoundException("Cashier", request.getReceiverId()));

            // Validate received items
            if (request.getReceivedItems() == null || request.getReceivedItems().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), "Bad Request",
                            "Received items cannot be empty", "/api/inventory-transfers/" + transferId + "/receive"));
            }

            InventoryTransfer transfer = transferService.receiveTransfer(
                    transferId,
                    receiver,
                    request.getReceivedItems()
            );

            // Calculate summary for logging
            int totalReceived = request.getReceivedItems().stream()
                    .mapToInt(InventoryTransferService.ReceiveItemDto::getReceivedQuantity)
                    .sum();
            int totalDamaged = request.getReceivedItems().stream()
                    .mapToInt(item -> item.getDamagedQuantity() != null ? item.getDamagedQuantity() : 0)
                    .sum();

            log.info("Successfully received transfer {} - Received: {}, Damaged: {}, Value: {}",
                    transfer.getTransferNumber(),
                    totalReceived,
                    totalDamaged,
                    transfer.getTotalReceivedValue());

            return ResponseEntity.ok(transfer);
        } catch (ResourceNotFoundException e) {
            log.error("Resource not found: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorResponse.of(HttpStatus.NOT_FOUND.value(), "Not Found", e.getMessage(),
                        "/api/inventory-transfers/" + transferId + "/receive"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Invalid operation: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), "Bad Request", e.getMessage(),
                        "/api/inventory-transfers/" + transferId + "/receive"));
        } catch (Exception e) {
            log.error("Error receiving transfer {}: {}", transferId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error",
                        "Failed to receive transfer", "/api/inventory-transfers/" + transferId + "/receive"));
        }
    }

    /**
     * Cancel transfer (Enhanced - reverses inventory changes if shipped)
     */
    @PostMapping("/{transferId}/cancel")
    public ResponseEntity<?> cancelTransfer(
            @PathVariable Long transferId,
            @RequestBody CancellationRequest request) {

        try {
            if (request.getReason() == null || request.getReason().trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), "Bad Request",
                            "Cancellation reason is required", "/api/inventory-transfers/" + transferId + "/cancel"));
            }

            InventoryTransfer transfer = transferService.cancelTransfer(transferId, request.getReason());

            log.info("Successfully cancelled transfer {} - Status was: {}, Reason: {}",
                    transfer.getTransferNumber(),
                    transfer.getStatus(),
                    request.getReason());

            return ResponseEntity.ok(transfer);
        } catch (ResourceNotFoundException e) {
            log.error("Resource not found: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorResponse.of(HttpStatus.NOT_FOUND.value(), "Not Found", e.getMessage(),
                        "/api/inventory-transfers/" + transferId + "/cancel"));
        } catch (IllegalStateException e) {
            log.error("Invalid operation: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), "Bad Request", e.getMessage(),
                        "/api/inventory-transfers/" + transferId + "/cancel"));
        } catch (Exception e) {
            log.error("Error cancelling transfer {}: {}", transferId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error",
                        "Failed to cancel transfer", "/api/inventory-transfers/" + transferId + "/cancel"));
        }
    }

    /**
     * Complete transfer
     */
    @PostMapping("/{transferId}/complete")
    public ResponseEntity<?> completeTransfer(@PathVariable Long transferId) {
        try {
            InventoryTransfer transfer = transferService.completeTransfer(transferId);
            return ResponseEntity.ok(transfer);
        } catch (ResourceNotFoundException e) {
            log.error("Resource not found: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorResponse.of(HttpStatus.NOT_FOUND.value(), "Not Found", e.getMessage(),
                        "/api/inventory-transfers/" + transferId + "/complete"));
        } catch (IllegalStateException e) {
            log.error("Invalid operation: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), "Bad Request", e.getMessage(),
                        "/api/inventory-transfers/" + transferId + "/complete"));
        } catch (Exception e) {
            log.error("Error completing transfer", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error",
                        "Failed to complete transfer", "/api/inventory-transfers/" + transferId + "/complete"));
        }
    }

    /**
     * Get transfer inventory impact summary
     */
    @GetMapping("/{transferId}/inventory-impact")
    public ResponseEntity<TransferInventoryImpact> getTransferInventoryImpact(@PathVariable Long transferId) {
        try {
            Optional<InventoryTransfer> transferOpt = transferService.findById(transferId);
            if (transferOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            InventoryTransfer transfer = transferOpt.get();
            TransferInventoryImpact impact = TransferInventoryImpact.builder()
                    .transferNumber(transfer.getTransferNumber())
                    .status(transfer.getStatus())
                    .fromShopName(transfer.getFromShop().getName())
                    .toShopName(transfer.getToShop().getName())
                    .totalItems(transfer.getTotalItems())
                    .totalShippedItems(transfer.getTotalShippedItems())
                    .totalReceivedItems(transfer.getTotalReceivedItems())
                    .totalDamagedItems(transfer.getTotalDamagedItems())
                    .totalValue(transfer.getTotalValue())
                    .totalReceivedValue(transfer.getTotalReceivedValue())
                    .totalDamageValue(transfer.getTotalDamageValue())
                    .hasDiscrepancies(transfer.hasDiscrepancies())
                    .isFullyShipped(transfer.isFullyShipped())
                    .isFullyReceived(transfer.isFullyReceived())
                    .isOverdue(transfer.isOverdue())
                    .priority(transfer.getPriority().getDisplayName())
                    .transferType(transfer.getTransferType().getDisplayName())
                    .build();

            return ResponseEntity.ok(impact);
        } catch (Exception e) {
            log.error("Error getting inventory impact for transfer {}: {}", transferId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get transfer history with inventory changes
     */
    @GetMapping("/{transferId}/history")
    public ResponseEntity<TransferHistory> getTransferHistory(@PathVariable Long transferId) {
        try {
            Optional<InventoryTransfer> transferOpt = transferService.findById(transferId);
            if (transferOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            InventoryTransfer transfer = transferOpt.get();
            TransferHistory history = TransferHistory.builder()
                    .transferNumber(transfer.getTransferNumber())
                    .transferType(transfer.getTransferType())
                    .priority(transfer.getPriority())
                    .notes(transfer.getNotes())
                    .fromShopName(transfer.getFromShop().getName())
                    .toShopName(transfer.getToShop().getName())
                    .initiatedBy(transfer.getInitiatedBy().getUsername())
                    .requestedAt(transfer.getRequestedAt())
                    .approvedBy(transfer.getApprovedBy() != null ? transfer.getApprovedBy().getUsername() : null)
                    .approvedAt(transfer.getApprovedAt())
                    .shippedBy(transfer.getShippedBy() != null ? transfer.getShippedBy().getUsername() : null)
                    .shippedAt(transfer.getShippedAt())
                    .expectedDelivery(transfer.getExpectedDelivery())
                    .receivedBy(transfer.getReceivedBy() != null ? transfer.getReceivedBy().getUsername() : null)
                    .receivedAt(transfer.getReceivedAt())
                    .cancelledAt(transfer.getCancelledAt())
                    .cancellationReason(transfer.getCancellationReason())
                    .currentStatus(transfer.getStatus())
                    .isOverdue(transfer.isOverdue())
                    .isUrgent(transfer.isUrgent())
                    .build();

            return ResponseEntity.ok(history);
        } catch (Exception e) {
            log.error("Error getting transfer history for {}: {}", transferId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


}