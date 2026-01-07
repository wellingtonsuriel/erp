package com.pos_onlineshop.hybrid.dtos;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public  class ShipmentRequest {
    private Long shipperId;
    private LocalDateTime expectedDelivery;
}