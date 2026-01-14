package com.pos_onlineshop.hybrid.dtos;

import com.pos_onlineshop.hybrid.enums.PriceType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class SellingPriceUpdateRequest {
    private PriceType priceType;
    private BigDecimal sellingPrice;
    private BigDecimal basePrice;
    private List<Long> taxIds;
    private BigDecimal discountPercentage;
    private BigDecimal minSellingPrice;
    private BigDecimal maxSellingPrice;
    private Integer quantityBreak;
    private BigDecimal bulkPrice;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private Boolean active;
    private Integer priority;
    private String updatedBy;
    private String notes;
}
