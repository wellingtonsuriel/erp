package com.pos_onlineshop.hybrid.dtos;

import lombok.Data;

@Data
public class VoidTransactionRequest {
    private Long cashierId;
    private String reason;
}