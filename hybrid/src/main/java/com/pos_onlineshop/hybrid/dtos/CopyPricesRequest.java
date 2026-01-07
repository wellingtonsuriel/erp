package com.pos_onlineshop.hybrid.dtos;

import lombok.Data;

@Data
public  class CopyPricesRequest {
    private Long sourceShopId;
    private Long targetShopId;
    private String createdBy;
}