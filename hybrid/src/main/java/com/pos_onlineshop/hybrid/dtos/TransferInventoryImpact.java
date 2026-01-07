package com.pos_onlineshop.hybrid.dtos;



import com.pos_onlineshop.hybrid.enums.TransferStatus;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;

/**
 * DTO representing the inventory impact of a transfer
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferInventoryImpact {

    private String transferNumber;
    private TransferStatus status;
    private String fromShopName;
    private String toShopName;

    // Quantity tracking
    private Integer totalItems;
    private Integer totalShippedItems;
    private Integer totalReceivedItems;
    private Integer totalDamagedItems;
    private Integer totalPendingItems;
    private Integer totalMissingItems;

    // Value tracking
    private BigDecimal totalValue;
    private BigDecimal totalReceivedValue;
    private BigDecimal totalDamageValue;
    private BigDecimal totalLossValue;

    // Status flags
    private Boolean hasDiscrepancies;
    private Boolean isFullyShipped;
    private Boolean isFullyReceived;
    private Boolean isOverdue;

    // Additional metrics
    private Double completionPercentage;
    private String priority;
    private String transferType;

    // Helper methods for calculated values
    public Integer getTotalPendingItems() {
        if (totalItems != null && totalShippedItems != null) {
            return totalItems - totalShippedItems;
        }
        return 0;
    }

    public Integer getTotalMissingItems() {
        if (totalShippedItems != null && totalReceivedItems != null && totalDamagedItems != null) {
            return totalShippedItems - totalReceivedItems - totalDamagedItems;
        }
        return 0;
    }

    public BigDecimal getTotalLossValue() {
        if (totalValue != null && totalReceivedValue != null) {
            return totalValue.subtract(totalReceivedValue);
        }
        return BigDecimal.ZERO;
    }

    public Double getCompletionPercentage() {
        if (totalItems != null && totalItems > 0 && totalReceivedItems != null) {
            return (totalReceivedItems.doubleValue() / totalItems.doubleValue()) * 100.0;
        }
        return 0.0;
    }
}