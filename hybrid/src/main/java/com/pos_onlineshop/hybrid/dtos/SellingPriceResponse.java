package com.pos_onlineshop.hybrid.dtos;

import com.pos_onlineshop.hybrid.enums.PriceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SellingPriceResponse {
    private Long id;
    private Long productId;
    private String productName;
    private Long shopId;
    private String shopName;
    private Long currencyId;
    private String currencyCode;
    private PriceType priceType;
    private BigDecimal sellingPrice;
    private BigDecimal basePrice;
    private List<TaxResponse> taxes;
    private BigDecimal discountPercentage;
    private BigDecimal finalPrice;
    private BigDecimal minSellingPrice;
    private BigDecimal maxSellingPrice;
    private Integer quantityBreak;
    private BigDecimal bulkPrice;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private boolean active;
    private boolean currentlyEffective;
    private Integer priority;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String notes;
}
