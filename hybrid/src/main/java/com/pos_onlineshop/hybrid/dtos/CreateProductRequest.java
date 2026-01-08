package com.pos_onlineshop.hybrid.dtos;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateProductRequest {
    private String name;
    private String description;
    private String category;
    private String imageUrl;
    private String barcode;
    private String sku;
    private boolean isWeighable = false;
    private Integer maxStock;
    private Integer minStock;
    private BigDecimal weight;
    private String unitOfMeasure;
    private String actualMeasure;
}