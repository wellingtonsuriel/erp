package com.pos_onlineshop.hybrid.dtos;

import com.pos_onlineshop.hybrid.enums.PriceType;
import lombok.Data;

import java.math.BigDecimal;

@Data
public  class BulkUpdateRequest {
    private PriceType priceType;
    private BigDecimal percentage;
    private String updatedBy;
}
