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

    @PositiveOrZero(message = "Quantity must be zero or positive")
    private Integer quantity;

    @PositiveOrZero(message = "In-transit quantity must be zero or positive")
    private Integer inTransitQuantity;

    private BigDecimal unitPrice;

    private LocalDateTime expiryDate;
}
