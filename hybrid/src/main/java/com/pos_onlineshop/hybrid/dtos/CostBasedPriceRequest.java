package com.pos_onlineshop.hybrid.dtos;

import lombok.Data;

import java.math.BigDecimal;

@Data
public  class CostBasedPriceRequest {
    private BigDecimal costPrice;
    private BigDecimal markupPercentage;
}
