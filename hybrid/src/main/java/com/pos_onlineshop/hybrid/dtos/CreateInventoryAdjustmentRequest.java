package com.pos_onlineshop.hybrid.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateInventoryAdjustmentRequest {

    /** Caller-supplied, unique - doubles as the GL idempotency key, so a retried request
     * with the same reference replays the original adjustment instead of double-counting it. */
    @NotBlank(message = "Reference is required")
    private String reference;

    @NotNull(message = "Shop is required")
    private Long shopId;

    @NotNull(message = "Product is required")
    private Long productId;

    /** Positive = surplus found, negative = shortage found. Must not be zero. */
    @NotNull(message = "Quantity delta is required")
    private Integer quantityDelta;

    @NotBlank(message = "A reason is required for a manual inventory adjustment")
    private String reason;

    /** Required when quantityDelta is positive - the cost basis for the new FIFO layer.
     * Ignored for a negative quantityDelta, which is costed from real FIFO layers instead. */
    @DecimalMin(value = "0.0001", message = "Unit cost must be positive")
    private BigDecimal unitCost;
}
