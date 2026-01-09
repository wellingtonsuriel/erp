package com.pos_onlineshop.hybrid.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryRequest {
    private Integer quantity;
    private Long supplierId;
    private Long currencyId;
    private BigDecimal unitPrice;
    private LocalDateTime expiryDate;
    private Integer reorderLevel;
    private Integer minStock;
    private Integer maxStock;
}
