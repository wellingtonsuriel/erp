package com.pos_onlineshop.hybrid.dtos;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class StartSessionRequest {
    private Long cashierId;
    private Long shopId;
    private String terminalId;
    private BigDecimal openingCash;
}