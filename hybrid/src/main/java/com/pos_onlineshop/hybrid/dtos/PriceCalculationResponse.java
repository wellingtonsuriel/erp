package com.pos_onlineshop.hybrid.dtos;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PriceCalculationResponse {
    private BigDecimal costPrice;
    private BigDecimal markupPercentage;
    private BigDecimal sellingPrice;
}