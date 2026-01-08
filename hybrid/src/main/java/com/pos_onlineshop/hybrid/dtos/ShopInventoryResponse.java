package com.pos_onlineshop.hybrid.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopInventoryResponse {
    private Long id;
    private Long shopId;
    private String shopCode;
    private String shopName;
    private Long productId;
    private String productName;
    private String productBarcode;
    private Long supplierId;
    private String supplierName;
    private Integer quantity;
    private Integer inTransitQuantity;
    private Long currencyId;
    private String currencyCode;
    private BigDecimal unitPrice;
    private LocalDateTime expiryDate;
    private Integer reorderLevel;
    private Integer minStock;
    private Integer maxStock;
    private LocalDateTime addedAt;
}
