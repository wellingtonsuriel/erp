package com.pos_onlineshop.hybrid.dtos;

import lombok.Data;

@Data
public class StockLimitsRequest {
    private Integer minStock;
    private Integer maxStock;
}
