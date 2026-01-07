package com.pos_onlineshop.hybrid.dtos;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PromotionalPriceRequest {
    private Long currencyId;
    private BigDecimal promotionalPrice;
    private LocalDateTime expiryDate;
    private String createdBy;
}