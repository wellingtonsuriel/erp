package com.pos_onlineshop.hybrid.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

@Data
public class ReceivePurchaseOrderRequest {

    /** Cashier id of the person receiving the goods. */
    @NotNull(message = "Receiver is required")
    private Long receivedById;

    @NotEmpty(message = "At least one line must be received")
    @Valid
    private List<Line> lines;

    @Data
    public static class Line {
        @NotNull(message = "Product is required")
        private Long productId;

        @NotNull(message = "Received quantity is required")
        @Positive(message = "Received quantity must be positive")
        private Integer receivedQuantity;
    }
}
