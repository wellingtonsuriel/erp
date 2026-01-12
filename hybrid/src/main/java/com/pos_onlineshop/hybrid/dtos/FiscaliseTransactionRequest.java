package com.pos_onlineshop.hybrid.dtos;

import com.pos_onlineshop.hybrid.enums.FiscalDocumentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FiscaliseTransactionRequest {
    private Long orderId;
    private Long saleId;
    private Long shopId;
    private Long fiscalDeviceId;  // Optional - will use shop's primary device if not specified
    private FiscalDocumentType documentType;
    private String taxpayerTin;  // Optional - for registered taxpayers
    private String taxpayerName;  // Optional
}
