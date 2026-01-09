package com.pos_onlineshop.hybrid.dtos;

import com.pos_onlineshop.hybrid.enums.PaymentMethod;
import com.pos_onlineshop.hybrid.enums.SaleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for sale response
 * Contains all sale information to be sent to the client
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleResponse {

    private Long id;

    private Long shopId;
    private String shopName;

    private Long customerId;
    private String customerName;
    private String customerCode;

    private Long productId;
    private String productName;
    private String productSku;

    private Integer quantity;

    private Long currencyId;
    private String currencyCode;
    private String currencySymbol;

    private BigDecimal unitPrice;
    private BigDecimal totalPrice;

    private PaymentMethod paymentMethod;
    private SaleType saleType;

    private LocalDateTime addedAt;
    private LocalDateTime updatedAt;
}
