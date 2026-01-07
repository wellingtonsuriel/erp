package com.pos_onlineshop.hybrid.dtos;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateProductRequest {
    private String name;
    private String description;
    private BigDecimal price;
    private String baseCurrencyCode;
    private String category;
    private String imageUrl;
    private String barcode;
    private String sku;
    private BigDecimal taxRate = new BigDecimal("0.00");
    private boolean isWeighable = false;
    private BigDecimal minQuantity = BigDecimal.ONE;
}