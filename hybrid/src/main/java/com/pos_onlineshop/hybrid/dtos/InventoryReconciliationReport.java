package com.pos_onlineshop.hybrid.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The three-way inventory reconciliation view: quantity ("how many"), valuation ("how much is
 * the stock worth"), and the GL ("what is the accounting value") - see INVENTORY_VALUATION.md.
 * inventoryValue and glInventoryAssetBalance must agree; variance is reported explicitly
 * (never silently forced to zero or hidden) exactly like every other reconciliation in this
 * codebase (see ControlAccountReconciliationReport).
 *
 * Quantity is always a live, current snapshot (InventoryTotal has no historical point-in-time
 * query); glInventoryAssetBalance is computed as of asOfDate, mirroring
 * ControlAccountReconciliationService's own documented limitation for the same reason.
 */
@Data
@Builder
public class InventoryReconciliationReport {
    private LocalDate asOfDate;

    private Integer onHandQuantity;
    private Integer reservedQuantity;
    private Integer availableQuantity;

    private BigDecimal inventoryValue;
    private BigDecimal glInventoryAssetBalance;
    private BigDecimal variance;
    private boolean reconciled;

    /** Per (shop, product) valuation detail, for drilling into which shops/products make up
     * the total - see InventoryValuationResponse.unvaluedQuantity for spotting a specific gap. */
    private List<InventoryValuationResponse> lines;
}
