package com.pos_onlineshop.hybrid.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class BankAccountResponse {
    private Long id;
    private String accountName;
    private String accountNumber;
    private String accountType;
    private String glAccountCode;
    private String currencyCode;
    private Long shopId;
    private String shopName;
    private BigDecimal openingBalance;
    private BigDecimal currentBalance;
    private boolean active;
    private LocalDateTime createdAt;
}
