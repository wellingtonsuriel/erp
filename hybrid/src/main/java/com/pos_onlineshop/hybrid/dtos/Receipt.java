package com.pos_onlineshop.hybrid.dtos;

import com.pos_onlineshop.hybrid.enums.PaymentMethod;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class Receipt {
    private String receiptNumber;
    private String storeName;
    private String storeAddress;
    private String storePhone;
    private LocalDateTime timestamp;
    private String cashierName;
    private List<ReceiptLine> lines;
    private BigDecimal subtotal;
    private BigDecimal tax;
    private BigDecimal total;
    private PaymentMethod paymentMethod;
    private BigDecimal cashGiven;
    private BigDecimal change;
    private String thankYouMessage;
    private String barcode;

    @Data
    @Builder
    public static class ReceiptLine {
        private String productName;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal lineTotal;
    }
}