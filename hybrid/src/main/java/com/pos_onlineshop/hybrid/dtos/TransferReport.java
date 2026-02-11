package com.pos_onlineshop.hybrid.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferReport {

    private Integer totalTransfers;
    private Map<String, Long> transfersByStatus;
    private Map<String, Long> transfersByType;
    private BigDecimal totalTransferValue;
    private BigDecimal totalReceivedValue;
    private BigDecimal totalDamageValue;
    private Integer overdueTransferCount;
    private List<TransferReportItem> transfers;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private LocalDateTime generatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransferReportItem {
        private Long transferId;
        private String transferNumber;
        private String fromShopName;
        private String toShopName;
        private String status;
        private String transferType;
        private String priority;
        private Integer totalItems;
        private Integer shippedItems;
        private Integer receivedItems;
        private Integer damagedItems;
        private BigDecimal totalValue;
        private LocalDateTime requestedAt;
        private LocalDateTime shippedAt;
        private LocalDateTime receivedAt;
        private Boolean isOverdue;
    }
}
