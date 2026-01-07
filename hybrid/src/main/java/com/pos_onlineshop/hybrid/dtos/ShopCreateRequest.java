package com.pos_onlineshop.hybrid.dtos;

import com.pos_onlineshop.hybrid.enums.ShopType;
import lombok.Data;

@Data
public  class ShopCreateRequest {
    private String name;
    private String code;
    private String address;
    private String phoneNumber;
    private String email;
    private String openingTime;
    private String closingTime;
    private ShopType type;
    private boolean active = true;
    private Integer maxCashiers;
    private Integer storageCapacity;
    private Long defaultCurrencyId; // Use currency ID instead of object// You might want to create a proper Currency DTO
}