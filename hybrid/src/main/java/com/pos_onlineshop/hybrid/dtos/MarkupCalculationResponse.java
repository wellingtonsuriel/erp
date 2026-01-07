package com.pos_onlineshop.hybrid.dtos;

import lombok.Data;

import java.math.BigDecimal;

@Data
public  class MarkupCalculationResponse {
    private BigDecimal costPrice;
    private BigDecimal sellingPrice;
    private BigDecimal markupPercentage;
}