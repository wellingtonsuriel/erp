package com.pos_onlineshop.hybrid.dtos;

import lombok.Data;

@Data
public class AddToCartRequest {
    private Long productId;
    private Integer quantity = 1;
}