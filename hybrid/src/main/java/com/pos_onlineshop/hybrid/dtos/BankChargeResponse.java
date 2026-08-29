package com.pos_onlineshop.hybrid.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class BankChargeResponse {
    private Long id;
    private String referenceNumber;
    private Long bankAccountId;
    private String bankAccountName;
    private BigDecimal amount;
    private String currencyCode;
    private LocalDate chargeDate;
    private String description;
    private Long createdById;
    private String createdByUsername;
    private LocalDateTime createdAt;
    private Long journalEntryId;
    private Long journalEntryNumber;
}
