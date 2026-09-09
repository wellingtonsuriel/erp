package com.pos_onlineshop.hybrid.dtos;

import lombok.Data;

@Data
public class UpdateCurrencyRequest {
    private String name;
    private String symbol;
    private Integer decimalPlaces;
    private boolean active = true;
    private Integer displayOrder;

    /** Explicit request to promote this currency to the system's base currency (demoting
     * whichever one currently holds that role). null/omitted leaves base-currency status
     * unchanged; this is the only way to reassign it - see CurrencyService.createCurrency,
     * which deliberately refuses to let an unrelated currency-creation call silently steal
     * base-currency status as a side effect. */
    private Boolean baseCurrency;
}