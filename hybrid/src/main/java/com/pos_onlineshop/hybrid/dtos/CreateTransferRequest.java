package com.pos_onlineshop.hybrid.dtos;

import com.pos_onlineshop.hybrid.enums.TransferPriority;
import com.pos_onlineshop.hybrid.enums.TransferType;
import lombok.Data;

@Data
public  class CreateTransferRequest {
    private Long fromShopId;
    private Long toShopId;
    private Long initiatorId;
    private TransferType transferType;
    private TransferPriority priority;
    private String notes;
}