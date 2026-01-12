package com.pos_onlineshop.hybrid.dtos;

import com.pos_onlineshop.hybrid.enums.OrderStatus;
import com.pos_onlineshop.hybrid.enums.PaymentMethod;
import com.pos_onlineshop.hybrid.enums.SalesChannel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for order response
 * Contains all order information to be sent to the client
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponse {

    private Long id;

    private Long userId;
    private String username;

    private Long customerId;
    private String customerName;
    private String customerCode;

    private Long shopId;
    private String shopName;

    private Long cashierId;
    private String cashierName;

    private Long currencyId;
    private String currencyCode;
    private String currencySymbol;

    private BigDecimal exchangeRate;
    private BigDecimal totalAmount;
    private BigDecimal taxAmount;

    private OrderStatus status;
    private PaymentMethod paymentMethod;
    private SalesChannel salesChannel;

    private String shippingAddress;
    private String storeLocation;
    private String receiptNumber;

    private BigDecimal cashGiven;
    private BigDecimal changeAmount;

    private boolean isPickup;

    private List<OrderLineResponse> orderLines;

    private LocalDateTime orderDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderLineResponse {
        private Long id;
        private Long productId;
        private String productName;
        private String productSku;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal subtotal;
        private BigDecimal taxRate;
    }
}
