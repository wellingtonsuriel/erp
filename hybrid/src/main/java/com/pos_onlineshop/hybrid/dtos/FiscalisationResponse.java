package com.pos_onlineshop.hybrid.dtos;

import com.pos_onlineshop.hybrid.enums.FiscalDocumentType;
import com.pos_onlineshop.hybrid.enums.FiscalStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FiscalisationResponse {
    private Long id;
    private String fiscalCode;
    private String receiptNumber;
    private String verificationCode;
    private FiscalDocumentType documentType;
    private FiscalStatus status;

    private Long orderId;
    private Long saleId;
    private Long shopId;
    private String shopName;
    private Long fiscalDeviceId;
    private String fiscalDeviceSerial;

    private BigDecimal subtotalAmount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private BigDecimal taxRate;
    private String currency;

    private String taxpayerTin;
    private String taxpayerName;
    private String businessTin;
    private String businessName;

    private LocalDateTime fiscalDate;
    private LocalDateTime verificationDate;
    private String qrCodeData;
    private String verificationUrl;
    private String errorMessage;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
