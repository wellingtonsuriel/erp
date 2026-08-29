package com.pos_onlineshop.hybrid.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class LoyaltyTransactionResponse {
    private Long id;
    private Long customerId;
    private String transactionType;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private String referenceType;
    private Long referenceId;
    private String description;
    private LocalDate transactionDate;
    private Long createdById;
    private String createdByUsername;
    private LocalDateTime createdAt;
    private Long journalEntryId;
    private Long journalEntryNumber;
}
