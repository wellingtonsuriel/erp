package com.pos_onlineshop.hybrid.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class CreatePurchaseOrderRequest {

    @NotNull(message = "Supplier is required")
    private Long supplierId;

    @NotNull(message = "Shop is required")
    private Long shopId;

    @NotNull(message = "Currency is required")
    private Long currencyId;

    private LocalDate expectedDeliveryDate;

    private String notes;

    /** Cashier id of the creator, if known (used for audit only - not an approval). */
    private Long createdById;

    @NotEmpty(message = "A purchase order must have at least one line")
    @Valid
    private List<Line> lines;

    @Data
    public static class Line {
        @NotNull(message = "Product is required")
        private Long productId;

        @NotNull(message = "Quantity is required")
        @Positive(message = "Quantity must be positive")
        private Integer quantity;

        @NotNull(message = "Unit cost is required")
        @Positive(message = "Unit cost must be positive")
        private BigDecimal unitCost;
    }
}
