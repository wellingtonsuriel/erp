package com.pos_onlineshop.hybrid.dtos;

import com.pos_onlineshop.hybrid.enums.AmountSource;
import com.pos_onlineshop.hybrid.enums.DebitCredit;
import com.pos_onlineshop.hybrid.enums.ShopRole;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PostingRuleLineRequest {

    @NotNull(message = "Account is required")
    private Long accountId;

    @NotNull(message = "Debit/credit side is required")
    private DebitCredit side;

    @NotNull(message = "Amount source is required")
    private AmountSource amountSource;

    private int sequence;

    private ShopRole shopRole = ShopRole.SOURCE;
}
