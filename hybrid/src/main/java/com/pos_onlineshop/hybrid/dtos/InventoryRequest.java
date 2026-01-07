package com.pos_onlineshop.hybrid.dtos;

import lombok.Data;

@Data
public class InventoryRequest {
    private Integer quantity;
    private Integer reorderLevel;
    private Integer minStock;
    private Integer maxStock;
}
