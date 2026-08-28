package com.pos_onlineshop.hybrid.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class SupplierInvoiceResponse {
    private Long id;
    private String invoiceNumber;
    private Long supplierId;
    private String supplierName;
    private Long shopId;
    private Long purchaseOrderId;
    private String purchaseOrderNumber;
    private String currencyCode;
    private LocalDate invoiceDate;
    private LocalDate dueDate;
    private BigDecimal subtotalAmount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private BigDecimal amountPaid;
    private BigDecimal outstandingAmount;
    private String status;
    private String voidedReason;
}
