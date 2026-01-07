package com.pos_onlineshop.hybrid.dtos;

import lombok.Data;

import java.math.BigDecimal;

@Data
public  class BulkPriceRequest {
    private Long currencyId;
    private BigDecimal regularPrice;
    private BigDecimal bulkPrice;
    private Integer quantityBreak;
    private String createdBy;
}
