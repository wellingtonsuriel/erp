package com.pos_onlineshop.hybrid.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class SupplierDebitNoteResponse {
    private Long id;
    private String debitNoteNumber;
    private Long supplierId;
    private String supplierName;
    private Long invoiceId;
    private String invoiceNumber;
    private String currencyCode;
    private BigDecimal amount;
    private String reason;
    private LocalDate issueDate;
    private String status;
    private String voidedReason;
    private LocalDateTime createdAt;
    private Long postedJournalEntryId;
    private Long postedJournalEntryNumber;
}
