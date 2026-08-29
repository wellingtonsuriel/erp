package com.pos_onlineshop.hybrid.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class InventoryMovementResponse {
    private Long id;
    private Long shopId;
    private String shopName;
    private Long productId;
    private String productName;
    private String movementType;
    private Integer quantity;
    private BigDecimal unitCost;
    private String reference;
    private LocalDate transactionDate;
    private LocalDateTime createdAt;
}
