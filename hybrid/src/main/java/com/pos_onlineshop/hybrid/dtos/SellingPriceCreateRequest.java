package com.pos_onlineshop.hybrid.dtos;

import com.pos_onlineshop.hybrid.enums.PriceType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public  class SellingPriceCreateRequest {
    private Long productId;
    private Long shopId;
    private Long currencyId;
    private PriceType priceType;
    private BigDecimal sellingPrice;
    private BigDecimal costPrice;
    private BigDecimal markupPercentage;
    private BigDecimal discountPercentage;
    private BigDecimal minSellingPrice;
    private BigDecimal maxSellingPrice;
    private Integer quantityBreak;
    private BigDecimal bulkPrice;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private Integer priority;
    private String createdBy;
    private String notes;
}