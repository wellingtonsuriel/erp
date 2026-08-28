package com.pos_onlineshop.hybrid.dtos;

import com.pos_onlineshop.hybrid.enums.AccountType;
import com.pos_onlineshop.hybrid.enums.DebitCredit;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AccountResponse {
    private Long id;
    private String code;
    private String name;
    private AccountType accountType;
    private DebitCredit normalBalance;
    private Long parentAccountId;
    private String parentAccountCode;
    private boolean controlAccount;
    private boolean costOfGoodsSold;
    private boolean active;
    private boolean hasJournalHistory;
}
