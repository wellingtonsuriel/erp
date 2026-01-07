package com.pos_onlineshop.hybrid.dtos;

import com.pos_onlineshop.hybrid.services.InventoryTransferService;
import lombok.Data;

import java.util.List;

@Data
public  class ReceiveTransferRequest {
    private Long receiverId;
    private List<InventoryTransferService.ReceiveItemDto> receivedItems;
}