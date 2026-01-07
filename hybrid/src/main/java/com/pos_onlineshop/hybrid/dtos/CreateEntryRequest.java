package com.pos_onlineshop.hybrid.dtos;

import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.enums.EntryType;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateEntryRequest {
    private EntryType type;
    private BigDecimal amount;
    private String description;
    private Currency currency;
    private Long userId;
    private String referenceType;
    private Long referenceId;
}