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
public class StockSummaryReport {

    private Integer totalProducts;
    private Integer totalStockUnits;
    private BigDecimal totalStockValue;
    private Integer activeShopCount;
    private Integer lowStockItemCount;
    private Integer outOfStockItemCount;
    private List<ShopStockSummary> shopBreakdown;
    private LocalDateTime generatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShopStockSummary {
        private Long shopId;
        private String shopName;
        private String shopCode;
        private String shopType;
        private Integer productCount;
        private Integer totalStockUnits;
        private BigDecimal totalStockValue;
    }
}
