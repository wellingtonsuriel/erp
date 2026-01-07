package com.pos_onlineshop.hybrid.dtos;

import com.pos_onlineshop.hybrid.enums.OrderStatus;
import lombok.Data;

@Data
public class UpdateStatusRequest {
    private OrderStatus status;
}
