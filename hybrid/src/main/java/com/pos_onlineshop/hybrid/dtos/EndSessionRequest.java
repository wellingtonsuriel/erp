package com.pos_onlineshop.hybrid.dtos;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class EndSessionRequest {
    private BigDecimal closingCash;
    private String notes;
}
