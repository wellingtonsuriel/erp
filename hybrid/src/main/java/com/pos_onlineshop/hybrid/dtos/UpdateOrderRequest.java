package com.pos_onlineshop.hybrid.dtos;

import com.pos_onlineshop.hybrid.enums.OrderStatus;
import com.pos_onlineshop.hybrid.enums.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for updating an existing order
 * All fields are optional - only provided fields will be updated
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateOrderRequest {

    private OrderStatus status;

    private PaymentMethod paymentMethod;

    private String shippingAddress;

    @DecimalMin(value = "0.0", message = "Cash given must be positive")
    private BigDecimal cashGiven;

    private Long customerId;
}
