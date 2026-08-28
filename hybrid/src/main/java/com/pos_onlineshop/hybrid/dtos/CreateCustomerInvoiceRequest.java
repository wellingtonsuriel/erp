package com.pos_onlineshop.hybrid.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class CreateCustomerInvoiceRequest {

    @NotBlank(message = "Invoice number is required")
    private String invoiceNumber;

    @NotNull(message = "Customer is required")
    private Long customerId;

    @NotNull(message = "Shop is required")
    private Long shopId;

    @NotNull(message = "Currency is required")
    private Long currencyId;

    @NotNull(message = "Invoice date is required")
    private LocalDate invoiceDate;

    @NotNull(message = "Due date is required")
    private LocalDate dueDate;

    @NotNull(message = "Subtotal amount is required")
    @Positive(message = "Subtotal amount must be positive")
    private BigDecimal subtotalAmount;

    @PositiveOrZero(message = "Tax amount cannot be negative")
    private BigDecimal taxAmount = BigDecimal.ZERO;

    private String notes;

    @Valid
    private List<Line> lines;

    @Data
    public static class Line {
        @NotNull
        private Long productId;
        @NotNull
        @Positive
        private Integer quantity;
        @NotNull
        @Positive
        private BigDecimal unitPrice;
    }
}
