package com.pos_onlineshop.hybrid.dtos;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SetPriceRequest {
    private String currencyCode;
    private BigDecimal price;
}
