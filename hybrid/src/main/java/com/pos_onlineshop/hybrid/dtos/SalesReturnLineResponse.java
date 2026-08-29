package com.pos_onlineshop.hybrid.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class SalesReturnLineResponse {
    private Long id;
    private Long orderLineId;
    private String productName;
    private Integer quantityReturned;
    private BigDecimal unitPrice;
    private BigDecimal taxAmount;
    private BigDecimal unitCost;
}
