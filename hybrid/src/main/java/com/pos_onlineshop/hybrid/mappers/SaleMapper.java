package com.pos_onlineshop.hybrid.mappers;

import com.pos_onlineshop.hybrid.dtos.SaleResponse;
import com.pos_onlineshop.hybrid.sales.Sales;
import org.springframework.stereotype.Component;

/**
 * Mapper utility for converting between Sale entities and DTOs
 */
@Component
public class SaleMapper {

    /**
     * Convert Sales entity to SaleResponse DTO
     */
    public SaleResponse toResponse(Sales sale) {
        if (sale == null) {
            return null;
        }

        return SaleResponse.builder()
                .id(sale.getId())
                .shopId(sale.getShop() != null ? sale.getShop().getId() : null)
                .shopName(sale.getShop() != null ? sale.getShop().getName() : null)
                .customerId(sale.getCustomer() != null ? sale.getCustomer().getId() : null)
                .customerName(sale.getCustomer() != null ? sale.getCustomer().getName() : null)
                .customerCode(sale.getCustomer() != null ? sale.getCustomer().getCode() : null)
                .productId(sale.getProduct() != null ? sale.getProduct().getId() : null)
                .productName(sale.getProduct() != null ? sale.getProduct().getName() : null)
                .productSku(sale.getProduct() != null ? sale.getProduct().getSku() : null)
                .quantity(sale.getQuantity())
                .currencyId(sale.getCurrency() != null ? sale.getCurrency().getId() : null)
                .currencyCode(sale.getCurrency() != null ? sale.getCurrency().getCode() : null)
                .currencySymbol(sale.getCurrency() != null ? sale.getCurrency().getSymbol() : null)
                .unitPrice(sale.getUnitPrice())
                .totalPrice(sale.getTotalPrice())
                .paymentMethod(sale.getPaymentMethod())
                .saleType(sale.getSaleType())
                .addedAt(sale.getAddedAt())
                .updatedAt(sale.getUpdatedAt())
                .build();
    }
}
