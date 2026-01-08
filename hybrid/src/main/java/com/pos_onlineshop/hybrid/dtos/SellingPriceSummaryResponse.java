package com.pos_onlineshop.hybrid.dtos;

import com.pos_onlineshop.hybrid.enums.PriceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SellingPriceSummaryResponse {
    private Long id;
    private Long productId;
    private String productName;
    private PriceType priceType;
    private BigDecimal sellingPrice;
    private BigDecimal finalPrice;
    private boolean currentlyEffective;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private Integer priority;
}
