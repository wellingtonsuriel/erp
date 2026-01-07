package com.pos_onlineshop.hybrid.dtos;

import lombok.Data;

import java.math.BigDecimal;

@Data
public  class QuickSaleItem {
    private Long productId;
    private Integer quantity;
    private BigDecimal customPrice;
}