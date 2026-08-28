package com.pos_onlineshop.hybrid.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class JournalLineResponse {
    private Long id;
    private Long accountId;
    private String accountCode;
    private String accountName;
    private BigDecimal debitAmount;
    private BigDecimal creditAmount;
    private String currencyCode;
    private BigDecimal baseAmount;
    private BigDecimal exchangeRate;
    private Long costCenterShopId;
    private String costCenterShopName;
    private String memo;
}
