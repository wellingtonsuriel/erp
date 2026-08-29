package com.pos_onlineshop.hybrid.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/** Monetary view of one (shop, product) pair's on-hand stock - real FIFO cost-layer
 * valuation, computed by InventoryValuationService. unvaluedQuantity is never hidden: a
 * nonzero value means part of the on-hand quantity has no cost layer to price it (see
 * InventoryValuationService's class comment), reported explicitly rather than folded
 * silently into inventoryValue as if it were genuinely $0. */
@Data
@Builder
public class InventoryValuationResponse {
    private Long shopId;
    private String shopName;
    private Long productId;
    private String productName;
    private Integer onHand;
    private BigDecimal unitCost;
    private BigDecimal inventoryValue;
    private Integer unvaluedQuantity;
}
