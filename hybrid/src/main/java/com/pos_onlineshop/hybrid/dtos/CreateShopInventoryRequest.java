package com.pos_onlineshop.hybrid.dtos;

import jakarta.validation.constraints.NotNull;
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
public class CreateShopInventoryRequest {
    @NotNull(message = "Shop ID is required")
    private Long shopId;

    @NotNull(message = "Product ID is required")
    private Long productId;

    @NotNull(message = "Supplier ID is required")
    private Long supplierId;

    @NotNull(message = "Currency ID is required")
    private Long currencyId;

    @NotNull(message = "Quantity is required")
    @PositiveOrZero(message = "Quantity must be zero or positive")
    private Integer quantity;

    @PositiveOrZero(message = "In-transit quantity must be zero or positive")
    @Builder.Default
    private Integer inTransitQuantity = 0;

    @NotNull(message = "Unit price is required")
    private BigDecimal unitPrice;

    private LocalDateTime expiryDate;
}
