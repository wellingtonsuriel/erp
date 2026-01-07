package com.pos_onlineshop.hybrid.dtos;

import com.pos_onlineshop.hybrid.enums.PaymentMethod;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Data
@Builder
public class DailySummary {
    private LocalDate date;
    private String storeLocation;
    private Integer totalTransactions;
    private BigDecimal totalRevenue;
    private Map<PaymentMethod, BigDecimal> revenueByPaymentMethod;
    private Map<String, Integer> topSellingProducts;
    private BigDecimal cashInDrawer;
    private Integer refundsProcessed;
    private BigDecimal refundTotal;
    private String topCashier;
}