package com.pos_onlineshop.hybrid.dtos;

import com.pos_onlineshop.hybrid.enums.FiscalDeviceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FiscalDeviceResponse {
    private Long id;
    private String serialNumber;
    private String deviceName;
    private FiscalDeviceType deviceType;
    private Long shopId;
    private String shopName;
    private String zimraRegistrationNumber;
    private String fiscalMemoryId;
    private LocalDateTime certificationDate;
    private LocalDateTime certificationExpiryDate;
    private String manufacturer;
    private String model;
    private String firmwareVersion;
    private Boolean isActive;
    private Boolean isConnected;
    private Boolean isOperational;
    private Boolean isCertificationValid;
    private LocalDateTime lastConnectionTime;
    private String lastErrorMessage;
    private Long lastReceiptNumber;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
