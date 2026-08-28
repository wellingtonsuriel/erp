package com.pos_onlineshop.hybrid.dtos;

import com.pos_onlineshop.hybrid.enums.DebitCredit;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ManualJournalLineResponse {
    private Long id;
    private Long accountId;
    private String accountCode;
    private String accountName;
    private DebitCredit side;
    private BigDecimal amount;
    private String currencyCode;
    private Long costCenterShopId;
    private String costCenterShopName;
    private String memo;
}
