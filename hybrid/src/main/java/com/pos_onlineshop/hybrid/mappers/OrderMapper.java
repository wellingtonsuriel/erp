package com.pos_onlineshop.hybrid.mappers;

import com.pos_onlineshop.hybrid.dtos.OrderResponse;
import com.pos_onlineshop.hybrid.orderLines.OrderLine;
import com.pos_onlineshop.hybrid.orders.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Mapper utility for converting between Order entities and DTOs
 */
@Component
public class OrderMapper {

    /**
     * Convert Order entity to OrderResponse DTO
     */
    public OrderResponse toResponse(Order order) {
        if (order == null) {
            return null;
        }

        return OrderResponse.builder()
                .id(order.getId())
                .userId(order.getUser() != null ? order.getUser().getId() : null)
                .username(order.getUser() != null ? order.getUser().getUsername() : null)
                .customerId(order.getCustomer() != null ? order.getCustomer().getId() : null)
                .customerName(order.getCustomer() != null ? order.getCustomer().getName() : null)
                .customerCode(order.getCustomer() != null ? order.getCustomer().getCode() : null)
                .shopId(order.getShop() != null ? order.getShop().getId() : null)
                .shopName(order.getShop() != null ? order.getShop().getName() : null)
                .cashierId(order.getCashier() != null ? order.getCashier().getId() : null)
                .cashierName(order.getCashier() != null ? order.getCashier().getUser().getUsername() : null)
                .currencyId(order.getCurrency() != null ? order.getCurrency().getId() : null)
                .currencyCode(order.getCurrency() != null ? order.getCurrency().getCode() : null)
                .currencySymbol(order.getCurrency() != null ? order.getCurrency().getSymbol() : null)
                .exchangeRate(order.getExchangeRate())
                .totalAmount(order.getTotalAmount())
                .taxAmount(order.getTaxAmount())
                .status(order.getStatus())
                .paymentMethod(order.getPaymentMethod())
                .salesChannel(order.getSalesChannel())
                .shippingAddress(order.getShippingAddress())
                .storeLocation(order.getStoreLocation())
                .receiptNumber(order.getReceiptNumber())
                .cashGiven(order.getCashGiven())
                .changeAmount(order.getChangeAmount())
                .isPickup(order.isPickup())
                .orderLines(toOrderLineResponses(order.getOrderLines()))
                .orderDate(order.getOrderDate())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    /**
     * Convert list of OrderLine entities to OrderLineResponse DTOs
     */
    private List<OrderResponse.OrderLineResponse> toOrderLineResponses(List<OrderLine> orderLines) {
        if (orderLines == null) {
            return null;
        }

        return orderLines.stream()
                .map(this::toOrderLineResponse)
                .collect(Collectors.toList());
    }

    /**
     * Convert OrderLine entity to OrderLineResponse DTO
     */
    private OrderResponse.OrderLineResponse toOrderLineResponse(OrderLine orderLine) {
        if (orderLine == null) {
            return null;
        }

        return OrderResponse.OrderLineResponse.builder()
                .id(orderLine.getId())
                .productId(orderLine.getProduct() != null ? orderLine.getProduct().getId() : null)
                .productName(orderLine.getProductName())
                .productSku(orderLine.getProduct() != null ? orderLine.getProduct().getSku() : null)
                .quantity(orderLine.getQuantity())
                .unitPrice(orderLine.getUnitPrice())
                .subtotal(orderLine.getSubtotal())
                .taxRate(orderLine.getTaxRate())
                .build();
    }
}
