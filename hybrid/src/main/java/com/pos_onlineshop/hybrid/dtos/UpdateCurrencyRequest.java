package com.pos_onlineshop.hybrid.dtos;

import lombok.Data;

@Data
public class UpdateCurrencyRequest {
    private String name;
    private String symbol;
    private Integer decimalPlaces;
    private boolean active = true;
    private Integer displayOrder;
}