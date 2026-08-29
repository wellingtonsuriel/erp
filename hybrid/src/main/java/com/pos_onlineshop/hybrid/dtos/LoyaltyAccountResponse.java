package com.pos_onlineshop.hybrid.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class LoyaltyAccountResponse {
    private Long id;
    private Long customerId;
    private String customerName;
    private BigDecimal availableBalance;
    private BigDecimal totalEarned;
    private BigDecimal totalRedeemed;
    private BigDecimal totalExpired;
    private BigDecimal totalReversed;
}
