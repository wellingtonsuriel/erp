package com.pos_onlineshop.hybrid.dtos;

import lombok.Data;

@Data
public class CreateCurrencyRequest {
    private String code;
    private String name;
    private String symbol;
    private Integer decimalPlaces = 2;
    private boolean baseCurrency = false;
    private Integer displayOrder = 0;
}