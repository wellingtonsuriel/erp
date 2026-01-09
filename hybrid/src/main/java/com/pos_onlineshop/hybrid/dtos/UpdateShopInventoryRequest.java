package com.pos_onlineshop.hybrid.dtos;

import jakarta.validation.constraints.PositiveOrZero;
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
public class UpdateShopInventoryRequest {
    private Long supplierId;

    private Long currencyId;

    @PositiveOrZero(message = "In-transit quantity must be zero or positive")
    private Integer inTransitQuantity;

    private BigDecimal unitPrice;

    private LocalDateTime expiryDate;

    @PositiveOrZero(message = "Reorder level must be zero or positive")
    private Integer reorderLevel;

    @PositiveOrZero(message = "Min stock must be zero or positive")
    private Integer minStock;

    @PositiveOrZero(message = "Max stock must be zero or positive")
    private Integer maxStock;
}
