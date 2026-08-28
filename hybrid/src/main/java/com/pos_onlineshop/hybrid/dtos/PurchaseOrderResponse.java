package com.pos_onlineshop.hybrid.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PurchaseOrderResponse {
    private Long id;
    private String poNumber;
    private Long supplierId;
    private String supplierName;
    private Long shopId;
    private String shopName;
    private String currencyCode;
    private String status;
    private LocalDate orderDate;
    private LocalDate expectedDeliveryDate;
    private String createdByName;
    private String approvedByName;
    private LocalDateTime approvedAt;
    private String cancellationReason;
    private BigDecimal totalValue;
    private List<LineResponse> lines;

    @Data
    @Builder
    public static class LineResponse {
        private Long productId;
        private String productName;
        private Integer quantityOrdered;
        private Integer quantityReceived;
        private Integer outstandingQuantity;
        private BigDecimal unitCost;
        private BigDecimal lineTotal;
    }
}
