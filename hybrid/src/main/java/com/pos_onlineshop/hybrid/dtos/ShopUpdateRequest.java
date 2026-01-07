package com.pos_onlineshop.hybrid.dtos;

import lombok.Data;

@Data
public  class ShopUpdateRequest {
    private String name;
    private String address;
    private String phoneNumber;
    private String email;
    private String openingTime;
    private String closingTime;
    private boolean active;
    private Integer maxCashiers;
    private Integer storageCapacity;
    private Long defaultCurrencyId; // Use currency ID instead of object
    private Long managerId;
}