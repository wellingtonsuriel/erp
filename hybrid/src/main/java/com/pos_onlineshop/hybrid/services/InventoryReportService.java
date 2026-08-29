package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.dtos.InventoryBalanceResponse;
import com.pos_onlineshop.hybrid.dtos.InventoryMovementResponse;
import com.pos_onlineshop.hybrid.dtos.InventoryReconciliationReport;
import com.pos_onlineshop.hybrid.dtos.InventoryValuationResponse;
import com.pos_onlineshop.hybrid.inventoryMovement.InventoryMovement;
import com.pos_onlineshop.hybrid.inventoryMovement.InventoryMovementRepository;
import com.pos_onlineshop.hybrid.inventoryTotal.InventoryTotal;
import com.pos_onlineshop.hybrid.inventoryTotal.InventoryTotalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Read-side reporting over the three inventory authorities - quantity (InventoryTotal),
 * valuation (InventoryValuationService's FIFO cost layers), and the GL (via
 * ControlAccountReconciliationService's existing 1200 Inventory Asset computation) - kept
 * deliberately separate per report (see §26 of the inventory architecture note: quantity,
 * valuation, and GL reports must never be merged into one ambiguous report) except for
 * getReconciliation, whose entire purpose is comparing them side by side.
 */
@Service
@RequiredArgsConstructor
public class InventoryReportService {

    private final InventoryTotalRepository inventoryTotalRepository;
    private final InventoryValuationService inventoryValuationService;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final ControlAccountReconciliationService controlAccountReconciliationService;

    @Transactional(readOnly = true)
    public List<InventoryBalanceResponse> getBalances(Long shopId) {
        List<InventoryTotal> totals = shopId != null
                ? inventoryTotalRepository.findByShopIdWithDetails(shopId)
                : inventoryTotalRepository.findAllWithShopAndProduct();

        return totals.stream()
                .map(it -> InventoryBalanceResponse.builder()
                        .shopId(it.getShop().getId())
                        .shopName(it.getShop().getName())
                        .productId(it.getProduct().getId())
                        .productName(it.getProduct().getName())
                        .onHand(it.getTotalstock())
                        .reserved(it.getReservedStock())
                        .available(it.getAvailableStock())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public List<InventoryValuationResponse> getValuation(Long shopId) {
        List<InventoryTotal> totals = shopId != null
                ? inventoryTotalRepository.findByShopIdWithDetails(shopId)
                : inventoryTotalRepository.findAllWithShopAndProduct();

        return totals.stream()
                .filter(it -> it.getTotalstock() != null && it.getTotalstock() > 0)
                .map(it -> {
                    InventoryValuationService.ValuationResult result =
                            inventoryValuationService.getInventoryValueDetailed(it.getShop(), it.getProduct());
                    BigDecimal unitCost = inventoryValuationService.getUnitCost(it.getShop(), it.getProduct()).orElse(null);
                    return InventoryValuationResponse.builder()
                            .shopId(it.getShop().getId())
                            .shopName(it.getShop().getName())
                            .productId(it.getProduct().getId())
                            .productName(it.getProduct().getName())
                            .onHand(it.getTotalstock())
                            .unitCost(unitCost)
                            .inventoryValue(result.getTotalValue())
                            .unvaluedQuantity(result.getUnvaluedQuantity())
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<InventoryMovementResponse> getMovements(Long shopId, Long productId) {
        List<InventoryMovement> movements;
        if (shopId != null && productId != null) {
            movements = inventoryMovementRepository.findByShopIdAndProductIdOrderByIdAsc(shopId, productId);
        } else if (shopId != null) {
            movements = inventoryMovementRepository.findByShopIdOrderByIdDesc(shopId);
        } else if (productId != null) {
            movements = inventoryMovementRepository.findByProductIdOrderByIdDesc(productId);
        } else {
            movements = inventoryMovementRepository.findAllByOrderByIdDesc();
        }

        return movements.stream()
                .map(m -> InventoryMovementResponse.builder()
                        .id(m.getId())
                        .shopId(m.getShop().getId())
                        .shopName(m.getShop().getName())
                        .productId(m.getProduct().getId())
                        .productName(m.getProduct().getName())
                        .movementType(m.getMovementType().name())
                        .quantity(m.getQuantity())
                        .unitCost(m.getUnitCost())
                        .reference(m.getReference())
                        .transactionDate(m.getTransactionDate())
                        .createdAt(m.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    /** The three-way reconciliation view - see InventoryReconciliationReport's class comment. */
    @Transactional
    public InventoryReconciliationReport getReconciliation(LocalDate asOfDate) {
        List<InventoryTotal> totals = inventoryTotalRepository.findAllWithShopAndProduct();

        int onHand = totals.stream().mapToInt(it -> it.getTotalstock() != null ? it.getTotalstock() : 0).sum();
        int reserved = totals.stream().mapToInt(it -> it.getReservedStock() != null ? it.getReservedStock() : 0).sum();

        BigDecimal inventoryValue = inventoryValuationService.getTotalInventoryValue();
        BigDecimal glBalance = controlAccountReconciliationService.getInventoryAssetGlBalance(asOfDate);
        BigDecimal variance = inventoryValue.subtract(glBalance);

        List<InventoryValuationResponse> lines = getValuation(null);

        return InventoryReconciliationReport.builder()
                .asOfDate(asOfDate)
                .onHandQuantity(onHand)
                .reservedQuantity(reserved)
                .availableQuantity(onHand - reserved)
                .inventoryValue(inventoryValue)
                .glInventoryAssetBalance(glBalance)
                .variance(variance)
                .reconciled(variance.compareTo(BigDecimal.ZERO) == 0)
                .lines(lines)
                .build();
    }
}
