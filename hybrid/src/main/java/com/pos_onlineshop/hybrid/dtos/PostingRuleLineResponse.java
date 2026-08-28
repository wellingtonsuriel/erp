package com.pos_onlineshop.hybrid.dtos;

import com.pos_onlineshop.hybrid.enums.AmountSource;
import com.pos_onlineshop.hybrid.enums.DebitCredit;
import com.pos_onlineshop.hybrid.enums.ShopRole;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PostingRuleLineResponse {
    private Long id;
    private Long accountId;
    private String accountCode;
    private String accountName;
    private DebitCredit side;
    private AmountSource amountSource;
    private int sequence;
    private ShopRole shopRole;
}
