package com.pos_onlineshop.hybrid.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockValueReport {

    private BigDecimal totalInventoryValue;
    private Integer totalStockUnits;
    private Integer totalProducts;
    private List<ShopValueBreakdown> shopValues;
    private List<CategoryValueBreakdown> categoryValues;
    private LocalDateTime generatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShopValueBreakdown {
        private Long shopId;
        private String shopName;
        private String shopCode;
        private String shopType;
        private BigDecimal totalValue;
        private Integer totalUnits;
        private Integer productCount;
        private BigDecimal percentageOfTotal;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryValueBreakdown {
        private String category;
        private BigDecimal totalValue;
        private Integer totalUnits;
        private Integer productCount;
        private BigDecimal percentageOfTotal;
    }
}
