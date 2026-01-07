package com.pos_onlineshop.hybrid.dtos;

import com.pos_onlineshop.hybrid.enums.PaymentMethod;
import com.pos_onlineshop.hybrid.enums.SalesChannel;
import lombok.Data;

@Data
public class CreateOrderRequest {
    private String shippingAddress;
    private PaymentMethod paymentMethod;
    private SalesChannel salesChannel;
    private String currencyCode;
    private boolean isPickup;
}

