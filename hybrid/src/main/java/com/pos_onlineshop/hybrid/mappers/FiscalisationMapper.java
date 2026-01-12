package com.pos_onlineshop.hybrid.mappers;

import com.pos_onlineshop.hybrid.dtos.FiscalisationResponse;
import com.pos_onlineshop.hybrid.dtos.FiscalDeviceResponse;
import com.pos_onlineshop.hybrid.fiscalDevice.FiscalDevice;
import com.pos_onlineshop.hybrid.zimra.ZimraFiscalisation;
import org.springframework.stereotype.Component;

@Component
public class FiscalisationMapper {

    public FiscalisationResponse toResponse(ZimraFiscalisation fiscalisation) {
        if (fiscalisation == null) {
            return null;
        }

        return FiscalisationResponse.builder()
            .id(fiscalisation.getId())
            .fiscalCode(fiscalisation.getFiscalCode())
            .receiptNumber(fiscalisation.getReceiptNumber())
            .verificationCode(fiscalisation.getVerificationCode())
            .documentType(fiscalisation.getDocumentType())
            .status(fiscalisation.getStatus())
            .orderId(fiscalisation.getOrder() != null ? fiscalisation.getOrder().getId() : null)
            .saleId(fiscalisation.getSale() != null ? fiscalisation.getSale().getId() : null)
            .shopId(fiscalisation.getShop() != null ? fiscalisation.getShop().getId() : null)
            .shopName(fiscalisation.getShop() != null ? fiscalisation.getShop().getName() : null)
            .fiscalDeviceId(fiscalisation.getFiscalDevice() != null ? fiscalisation.getFiscalDevice().getId() : null)
            .fiscalDeviceSerial(fiscalisation.getFiscalDevice() != null ? fiscalisation.getFiscalDevice().getSerialNumber() : null)
            .subtotalAmount(fiscalisation.getSubtotalAmount())
            .taxAmount(fiscalisation.getTaxAmount())
            .totalAmount(fiscalisation.getTotalAmount())
            .taxRate(fiscalisation.getTaxRate())
            .currency(fiscalisation.getCurrency())
            .taxpayerTin(fiscalisation.getTaxpayerTin())
            .taxpayerName(fiscalisation.getTaxpayerName())
            .businessTin(fiscalisation.getBusinessTin())
            .businessName(fiscalisation.getBusinessName())
            .fiscalDate(fiscalisation.getFiscalDate())
            .verificationDate(fiscalisation.getVerificationDate())
            .qrCodeData(fiscalisation.getQrCodeData())
            .verificationUrl(fiscalisation.getVerificationUrl())
            .errorMessage(fiscalisation.getErrorMessage())
            .createdAt(fiscalisation.getCreatedAt())
            .updatedAt(fiscalisation.getUpdatedAt())
            .build();
    }

    public FiscalDeviceResponse toDeviceResponse(FiscalDevice device) {
        if (device == null) {
            return null;
        }

        return FiscalDeviceResponse.builder()
            .id(device.getId())
            .serialNumber(device.getSerialNumber())
            .deviceName(device.getDeviceName())
            .deviceType(device.getDeviceType())
            .shopId(device.getShop() != null ? device.getShop().getId() : null)
            .shopName(device.getShop() != null ? device.getShop().getName() : null)
            .zimraRegistrationNumber(device.getZimraRegistrationNumber())
            .fiscalMemoryId(device.getFiscalMemoryId())
            .certificationDate(device.getCertificationDate())
            .certificationExpiryDate(device.getCertificationExpiryDate())
            .manufacturer(device.getManufacturer())
            .model(device.getModel())
            .firmwareVersion(device.getFirmwareVersion())
            .isActive(device.getIsActive())
            .isConnected(device.getIsConnected())
            .isOperational(device.isOperational())
            .isCertificationValid(device.isCertificationValid())
            .lastConnectionTime(device.getLastConnectionTime())
            .lastErrorMessage(device.getLastErrorMessage())
            .lastReceiptNumber(device.getLastReceiptNumber())
            .createdAt(device.getCreatedAt())
            .updatedAt(device.getUpdatedAt())
            .build();
    }
}
