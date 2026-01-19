package com.pos_onlineshop.hybrid.dtos;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public  class AddItemRequest {
    private List<Long> productIds;
    private Integer quantity;
    private BigDecimal unitCost;
    private String notes;
}