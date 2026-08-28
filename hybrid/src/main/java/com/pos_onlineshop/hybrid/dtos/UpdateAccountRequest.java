package com.pos_onlineshop.hybrid.dtos;

import com.pos_onlineshop.hybrid.enums.AccountType;
import com.pos_onlineshop.hybrid.enums.DebitCredit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Account code is intentionally not editable here - a chart-of-accounts code is a stable
 * identifier referenced by posting rules and external reports. */
@Data
public class UpdateAccountRequest {

    @NotBlank(message = "Account name is required")
    private String name;

    @NotNull(message = "Account type is required")
    private AccountType accountType;

    @NotNull(message = "Normal balance is required")
    private DebitCredit normalBalance;

    private Long parentAccountId;

    private boolean controlAccount;
}
