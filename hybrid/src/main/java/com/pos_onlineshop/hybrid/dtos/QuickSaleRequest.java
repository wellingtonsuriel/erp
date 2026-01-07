package com.pos_onlineshop.hybrid.dtos;

import com.pos_onlineshop.hybrid.enums.PaymentMethod;
import com.pos_onlineshop.hybrid.services.POSService;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class QuickSaleRequest {
    private Long cashierId;
    private List<QuickSaleItem> items;
    private PaymentMethod paymentMethod;
    private BigDecimal cashGiven;
}