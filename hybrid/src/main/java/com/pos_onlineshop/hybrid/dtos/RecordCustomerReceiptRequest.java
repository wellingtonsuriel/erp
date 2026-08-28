package com.pos_onlineshop.hybrid.dtos;

import com.pos_onlineshop.hybrid.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class RecordCustomerReceiptRequest {

    @NotNull(message = "Invoice is required")
    private Long invoiceId;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @NotNull(message = "Currency is required")
    private Long currencyId;

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

    private LocalDate receiptDate;

    private String reference;

    private Long recordedById;

    private String notes;
}
