package com.pos_onlineshop.hybrid.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class CreateSupplierInvoiceRequest {

    @NotBlank(message = "Invoice number is required")
    private String invoiceNumber;

    @NotNull(message = "Supplier is required")
    private Long supplierId;

    @NotNull(message = "Shop is required")
    private Long shopId;

    /** Optional - if set, this invoice relates to goods already received under this PO and
     * does not post a second GL entry for the goods (see SupplierInvoiceService). */
    private Long purchaseOrderId;

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

    /** Optional line-item detail (informational for PO-linked invoices; not required for a
     * service/expense invoice with no discrete products). */
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
        private BigDecimal unitCost;
    }
}
