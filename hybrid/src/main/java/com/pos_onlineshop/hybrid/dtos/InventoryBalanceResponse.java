package com.pos_onlineshop.hybrid.dtos;

import lombok.Builder;
import lombok.Data;

/** Quantity-only view of one (shop, product) pair - "how many do we have," never a monetary
 * figure. See InventoryValuationResponse for the corresponding valuation view. */
@Data
@Builder
public class InventoryBalanceResponse {
    private Long shopId;
    private String shopName;
    private Long productId;
    private String productName;
    private Integer onHand;
    private Integer reserved;
    private Integer available;
}
