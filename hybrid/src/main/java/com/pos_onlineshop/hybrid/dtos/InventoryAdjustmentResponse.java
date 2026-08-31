package com.pos_onlineshop.hybrid.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class InventoryAdjustmentResponse {
    private Long id;
    private String reference;
    private Long shopId;
    private String shopName;
    private Long productId;
    private String productName;
    private int quantityDelta;
    private String reason;
    private BigDecimal unitCost;
    private BigDecimal totalValue;
    private boolean fullyCosted;

    private Long createdById;
    private String createdByUsername;
    private LocalDateTime createdAt;

    private Long postedJournalEntryId;
    private Long postedJournalEntryNumber;
}
