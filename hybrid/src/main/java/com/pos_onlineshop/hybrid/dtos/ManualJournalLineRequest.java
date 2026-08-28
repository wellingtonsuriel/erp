package com.pos_onlineshop.hybrid.dtos;

import com.pos_onlineshop.hybrid.enums.DebitCredit;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ManualJournalLineRequest {

    @NotNull(message = "Account is required")
    private Long accountId;

    @NotNull(message = "Debit/credit side is required")
    private DebitCredit side;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.0001", message = "Amount must be positive")
    private BigDecimal amount;

    private Long currencyId;

    private BigDecimal exchangeRate;

    private Long costCenterShopId;

    private String memo;
}
