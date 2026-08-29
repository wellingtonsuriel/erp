package com.pos_onlineshop.hybrid.dtos;

import com.pos_onlineshop.hybrid.enums.CashBankAccountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CreateBankAccountRequest {

    @NotBlank(message = "Account name is required")
    private String accountName;

    private String accountNumber;

    @NotNull(message = "Account type is required")
    private CashBankAccountType accountType;

    /** Defaults by accountType (CASH->1010, MOBILE_MONEY->1020, BANK->1030) if omitted. */
    private String glAccountCode;

    @NotNull(message = "Currency is required")
    private Long currencyId;

    private Long shopId;

    /** Posted as an opening-balance journal (via OpeningBalanceService) if positive. */
    @DecimalMin(value = "0.0", message = "Opening balance cannot be negative")
    private BigDecimal openingBalance;

    /** Required when openingBalance is positive. */
    private LocalDate openingBalanceDate;

    @NotNull(message = "Creator is required")
    private Long createdByUserId;
}
