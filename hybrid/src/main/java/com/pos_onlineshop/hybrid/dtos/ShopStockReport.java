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
public class ShopStockReport {

    private Long shopId;
    private String shopName;
    private String shopCode;
    private String shopType;
    private Integer totalProducts;
    private Integer totalStockUnits;
    private BigDecimal totalStockValue;
    private Integer lowStockProductCount;
    private Integer outOfStockProductCount;
    private List<ProductStockDetail> products;
    private LocalDateTime generatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductStockDetail {
        private Long productId;
        private String productName;
        private String productBarcode;
        private String productSku;
        private String category;
        private Integer currentStock;
        private BigDecimal unitPrice;
        private BigDecimal totalValue;
        private Integer reorderLevel;
        private Integer minStock;
        private Integer maxStock;
        private String stockStatus;
    }
}
