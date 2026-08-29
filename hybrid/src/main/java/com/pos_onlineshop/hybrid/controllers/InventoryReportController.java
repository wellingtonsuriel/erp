package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.InventoryBalanceResponse;
import com.pos_onlineshop.hybrid.dtos.InventoryMovementResponse;
import com.pos_onlineshop.hybrid.dtos.InventoryValuationResponse;
import com.pos_onlineshop.hybrid.services.InventoryReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * /api/inventory/*. Quantity, valuation, and audit-trail reads over the inventory subledger,
 * kept as three deliberately separate endpoints (see §26 of the inventory architecture note -
 * a quantity report, a valuation report, and a GL report must never be merged into one
 * ambiguous report). The combined three-way reconciliation view lives at
 * GET /api/reports/inventory-reconciliation (GLReportController), alongside every other
 * financial report, since comparing against the GL is squarely a reporting concern.
 */
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('INVENTORY_VIEW') or hasRole('ADMIN')")
public class InventoryReportController {

    private final InventoryReportService inventoryReportService;

    /** "How many do we have" - on-hand/reserved/available per (shop, product), never a
     * monetary figure. */
    @GetMapping("/balances")
    public List<InventoryBalanceResponse> balances(@RequestParam(required = false) Long shopId) {
        return inventoryReportService.getBalances(shopId);
    }

    /** "How much is the stock worth" - real FIFO cost-layer valuation per (shop, product). */
    @GetMapping("/valuation")
    public List<InventoryValuationResponse> valuation(@RequestParam(required = false) Long shopId) {
        return inventoryReportService.getValuation(shopId);
    }

    /** The append-only quantity audit trail (see InventoryMovement's class comment). */
    @GetMapping("/movements")
    public List<InventoryMovementResponse> movements(
            @RequestParam(required = false) Long shopId, @RequestParam(required = false) Long productId) {
        return inventoryReportService.getMovements(shopId, productId);
    }
}
