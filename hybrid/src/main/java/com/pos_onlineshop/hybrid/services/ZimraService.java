package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.dtos.FiscalisationResponse;
import com.pos_onlineshop.hybrid.dtos.FiscalDeviceResponse;
import com.pos_onlineshop.hybrid.dtos.FiscaliseTransactionRequest;
import com.pos_onlineshop.hybrid.enums.FiscalDocumentType;
import com.pos_onlineshop.hybrid.enums.FiscalStatus;
import com.pos_onlineshop.hybrid.fiscalDevice.FiscalDevice;
import com.pos_onlineshop.hybrid.mappers.FiscalisationMapper;
import com.pos_onlineshop.hybrid.orders.Order;
import com.pos_onlineshop.hybrid.repositories.FiscalDeviceRepository;
import com.pos_onlineshop.hybrid.repositories.OrderRepository;
import com.pos_onlineshop.hybrid.repositories.SalesRepository;
import com.pos_onlineshop.hybrid.repositories.ZimraFiscalisationRepository;
import com.pos_onlineshop.hybrid.sales.Sales;
import com.pos_onlineshop.hybrid.shop.Shop;
import com.pos_onlineshop.hybrid.zimra.ZimraFiscalisation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ZimraService {

    private final ZimraFiscalisationRepository fiscalisationRepository;
    private final FiscalDeviceRepository deviceRepository;
    private final OrderRepository orderRepository;
    private final SalesRepository salesRepository;
    private final FiscalisationMapper mapper;

    @Value("${zimra.business.tin:12345678}")
    private String businessTin;

    @Value("${zimra.business.name:POS Online Shop}")
    private String businessName;

    @Value("${zimra.business.address:Harare, Zimbabwe}")
    private String businessAddress;

    @Value("${zimra.tax.rate:15.0}")
    private BigDecimal defaultTaxRate;

    @Value("${zimra.auto-fiscalise:true}")
    private boolean autoFiscalise;

    /**
     * Fiscalise an order transaction
     */
    public FiscalisationResponse fiscaliseOrder(Long orderId, FiscaliseTransactionRequest request) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new RuntimeException("Order not found with id: " + orderId));

        // Check if already fiscalised
        Optional<ZimraFiscalisation> existing = fiscalisationRepository.findByOrderId(orderId);
        if (existing.isPresent() && existing.get().isFiscalised()) {
            log.info("Order {} already fiscalised", orderId);
            return mapper.toResponse(existing.get());
        }

        Shop shop = order.getShop();
        if (shop == null) {
            throw new RuntimeException("Order must have a shop for fiscalisation");
        }

        FiscalDevice device = getFiscalDevice(request.getFiscalDeviceId(), shop);
        FiscalDocumentType docType = request.getDocumentType() != null
            ? request.getDocumentType()
            : FiscalDocumentType.FISCAL_RECEIPT;

        ZimraFiscalisation fiscalisation = existing.orElse(new ZimraFiscalisation());
        fiscalisation.setOrder(order);
        fiscalisation.setShop(shop);
        fiscalisation.setFiscalDevice(device);
        fiscalisation.setDocumentType(docType);
        fiscalisation.setCashier(order.getCashier());
        fiscalisation.setCustomer(order.getCustomer());

        // Calculate amounts
        BigDecimal totalAmount = order.getTotalAmount();
        BigDecimal taxRate = order.getTaxAmount() != null && totalAmount.compareTo(BigDecimal.ZERO) > 0
            ? order.getTaxAmount().divide(totalAmount, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
            : defaultTaxRate;

        BigDecimal taxAmount = order.getTaxAmount() != null
            ? order.getTaxAmount()
            : totalAmount.multiply(taxRate).divide(BigDecimal.valueOf(100).add(taxRate), 2, RoundingMode.HALF_UP);

        BigDecimal subtotal = totalAmount.subtract(taxAmount);

        fiscalisation.setSubtotalAmount(subtotal);
        fiscalisation.setTaxAmount(taxAmount);
        fiscalisation.setTotalAmount(totalAmount);
        fiscalisation.setTaxRate(taxRate);
        fiscalisation.setCurrency(order.getCurrency() != null ? order.getCurrency().getCode() : "USD");
        fiscalisation.setExchangeRate(order.getExchangeRate());

        // Business information
        fiscalisation.setBusinessTin(businessTin);
        fiscalisation.setBusinessName(businessName);
        fiscalisation.setBusinessAddress(businessAddress);

        // Taxpayer information
        fiscalisation.setTaxpayerTin(request.getTaxpayerTin());
        fiscalisation.setTaxpayerName(request.getTaxpayerName());

        // Perform fiscalisation
        return performFiscalisation(fiscalisation, device);
    }

    /**
     * Fiscalise a sale transaction
     */
    public FiscalisationResponse fiscaliseSale(Long saleId, FiscaliseTransactionRequest request) {
        Sales sale = salesRepository.findById(saleId)
            .orElseThrow(() -> new RuntimeException("Sale not found with id: " + saleId));

        // Check if already fiscalised
        Optional<ZimraFiscalisation> existing = fiscalisationRepository.findBySaleId(saleId);
        if (existing.isPresent() && existing.get().isFiscalised()) {
            log.info("Sale {} already fiscalised", saleId);
            return mapper.toResponse(existing.get());
        }

        Shop shop = sale.getShop();
        if (shop == null) {
            throw new RuntimeException("Sale must have a shop for fiscalisation");
        }

        FiscalDevice device = getFiscalDevice(request.getFiscalDeviceId(), shop);
        FiscalDocumentType docType = request.getDocumentType() != null
            ? request.getDocumentType()
            : FiscalDocumentType.FISCAL_RECEIPT;

        ZimraFiscalisation fiscalisation = existing.orElse(new ZimraFiscalisation());
        fiscalisation.setSale(sale);
        fiscalisation.setShop(shop);
        fiscalisation.setFiscalDevice(device);
        fiscalisation.setDocumentType(docType);
        fiscalisation.setCustomer(sale.getCustomer());

        // Calculate amounts
        BigDecimal totalAmount = sale.getTotalPrice();
        BigDecimal taxAmount = totalAmount.multiply(defaultTaxRate)
            .divide(BigDecimal.valueOf(100).add(defaultTaxRate), 2, RoundingMode.HALF_UP);
        BigDecimal subtotal = totalAmount.subtract(taxAmount);

        fiscalisation.setSubtotalAmount(subtotal);
        fiscalisation.setTaxAmount(taxAmount);
        fiscalisation.setTotalAmount(totalAmount);
        fiscalisation.setTaxRate(defaultTaxRate);
        fiscalisation.setCurrency(sale.getCurrency() != null ? sale.getCurrency().getCode() : "USD");

        // Business information
        fiscalisation.setBusinessTin(businessTin);
        fiscalisation.setBusinessName(businessName);
        fiscalisation.setBusinessAddress(businessAddress);

        // Taxpayer information
        fiscalisation.setTaxpayerTin(request.getTaxpayerTin());
        fiscalisation.setTaxpayerName(request.getTaxpayerName());

        // Perform fiscalisation
        return performFiscalisation(fiscalisation, device);
    }

    /**
     * Core fiscalisation logic
     */
    private FiscalisationResponse performFiscalisation(ZimraFiscalisation fiscalisation, FiscalDevice device) {
        try {
            fiscalisation.setStatus(FiscalStatus.PROCESSING);
            fiscalisation.setFiscalDate(LocalDateTime.now());

            // Generate receipt number from device
            String receiptNumber = generateReceiptNumber(device, fiscalisation);
            fiscalisation.setReceiptNumber(receiptNumber);

            // Generate fiscal code (unique identifier)
            String fiscalCode = generateFiscalCode(fiscalisation);
            fiscalisation.setFiscalCode(fiscalCode);

            // Generate verification code
            String verificationCode = generateVerificationCode(fiscalisation);
            fiscalisation.setVerificationCode(verificationCode);

            // Generate digital signature
            String signature = generateDigitalSignature(fiscalisation);
            fiscalisation.setDigitalSignature(signature);

            // Generate QR code data
            String qrCode = generateQRCodeData(fiscalisation);
            fiscalisation.setQrCodeData(qrCode);

            // Set fiscal memory details
            fiscalisation.setFiscalMemoryNumber(device.getFiscalMemoryId());
            fiscalisation.setFiscalCounterValue(device.getLastReceiptNumber());

            // Simulate device communication
            boolean success = sendToFiscalDevice(fiscalisation, device);

            if (success) {
                fiscalisation.setStatus(FiscalStatus.FISCALISED);
                fiscalisation.setVerificationDate(LocalDateTime.now());
                log.info("Successfully fiscalised transaction: {}", fiscalCode);
            } else {
                fiscalisation.setStatus(FiscalStatus.FAILED);
                fiscalisation.setErrorMessage("Failed to communicate with fiscal device");
                fiscalisation.setRetryCount(fiscalisation.getRetryCount() + 1);
                log.error("Failed to fiscalise transaction: {}", fiscalCode);
            }

            ZimraFiscalisation saved = fiscalisationRepository.save(fiscalisation);
            return mapper.toResponse(saved);

        } catch (Exception e) {
            log.error("Error during fiscalisation", e);
            fiscalisation.setStatus(FiscalStatus.FAILED);
            fiscalisation.setErrorMessage(e.getMessage());
            fiscalisation.setRetryCount(fiscalisation.getRetryCount() + 1);
            ZimraFiscalisation saved = fiscalisationRepository.save(fiscalisation);
            return mapper.toResponse(saved);
        }
    }

    /**
     * Generate unique receipt number
     */
    private String generateReceiptNumber(FiscalDevice device, ZimraFiscalisation fiscalisation) {
        Long nextNumber = device.getNextReceiptNumber();
        deviceRepository.save(device);

        String prefix = fiscalisation.getShop().getCode();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return String.format("%s-%s-%08d", prefix, timestamp, nextNumber);
    }

    /**
     * Generate unique fiscal code
     */
    private String generateFiscalCode(ZimraFiscalisation fiscalisation) {
        String data = String.format("%s-%s-%s-%s",
            fiscalisation.getBusinessTin(),
            fiscalisation.getReceiptNumber(),
            fiscalisation.getTotalAmount(),
            fiscalisation.getFiscalDate().format(DateTimeFormatter.ISO_DATE_TIME)
        );
        return hashString(data).substring(0, 32).toUpperCase();
    }

    /**
     * Generate ZIMRA verification code
     */
    private String generateVerificationCode(ZimraFiscalisation fiscalisation) {
        String data = String.format("%s%s%s",
            fiscalisation.getFiscalCode(),
            fiscalisation.getBusinessTin(),
            fiscalisation.getFiscalDevice().getZimraRegistrationNumber()
        );
        return hashString(data).substring(0, 20).toUpperCase();
    }

    /**
     * Generate digital signature for the fiscal document
     */
    private String generateDigitalSignature(ZimraFiscalisation fiscalisation) {
        String signatureData = String.format("%s|%s|%s|%s|%s|%s",
            fiscalisation.getFiscalCode(),
            fiscalisation.getReceiptNumber(),
            fiscalisation.getTotalAmount(),
            fiscalisation.getTaxAmount(),
            fiscalisation.getBusinessTin(),
            fiscalisation.getFiscalDate().format(DateTimeFormatter.ISO_DATE_TIME)
        );
        return hashString(signatureData);
    }

    /**
     * Generate QR code data for customer verification
     */
    private String generateQRCodeData(ZimraFiscalisation fiscalisation) {
        return String.format(
            "ZIMRA|%s|%s|%s|%s|%s|%s|%s",
            fiscalisation.getVerificationCode(),
            fiscalisation.getReceiptNumber(),
            fiscalisation.getBusinessTin(),
            fiscalisation.getBusinessName(),
            fiscalisation.getTotalAmount(),
            fiscalisation.getCurrency(),
            fiscalisation.getFiscalDate().format(DateTimeFormatter.ISO_DATE_TIME)
        );
    }

    /**
     * Send fiscalisation data to fiscal device
     * In production, this would communicate with actual fiscal hardware/API
     */
    private boolean sendToFiscalDevice(ZimraFiscalisation fiscalisation, FiscalDevice device) {
        // Simulate device communication
        if (!device.isOperational()) {
            log.warn("Fiscal device {} is not operational", device.getSerialNumber());
            return false;
        }

        // In production, implement actual device protocol (RS232, USB, TCP/IP, REST API)
        // For now, simulate success
        device.setLastConnectionTime(LocalDateTime.now());
        device.setIsConnected(true);
        deviceRepository.save(device);

        log.info("Sent fiscalisation to device {}: Receipt {}",
            device.getSerialNumber(), fiscalisation.getReceiptNumber());
        return true;
    }

    /**
     * Get fiscal device for shop
     */
    private FiscalDevice getFiscalDevice(Long deviceId, Shop shop) {
        if (deviceId != null) {
            return deviceRepository.findById(deviceId)
                .orElseThrow(() -> new RuntimeException("Fiscal device not found: " + deviceId));
        }

        return deviceRepository.findPrimaryDeviceForShop(shop)
            .orElseThrow(() -> new RuntimeException("No operational fiscal device found for shop: " + shop.getName()));
    }

    /**
     * Retry failed fiscalisations
     */
    public List<FiscalisationResponse> retryFailedFiscalisations() {
        List<ZimraFiscalisation> failed = fiscalisationRepository.findFailedTransactionsForRetry();
        List<FiscalisationResponse> results = new ArrayList<>();

        for (ZimraFiscalisation fiscalisation : failed) {
            try {
                FiscalisationResponse response = performFiscalisation(fiscalisation, fiscalisation.getFiscalDevice());
                results.add(response);
            } catch (Exception e) {
                log.error("Failed to retry fiscalisation for {}", fiscalisation.getFiscalCode(), e);
            }
        }

        return results;
    }

    /**
     * Get fiscalisation by fiscal code
     */
    @Transactional(readOnly = true)
    public FiscalisationResponse getByFiscalCode(String fiscalCode) {
        ZimraFiscalisation fiscalisation = fiscalisationRepository.findByFiscalCode(fiscalCode)
            .orElseThrow(() -> new RuntimeException("Fiscalisation not found: " + fiscalCode));
        return mapper.toResponse(fiscalisation);
    }

    /**
     * Get fiscalisation by verification code
     */
    @Transactional(readOnly = true)
    public FiscalisationResponse getByVerificationCode(String verificationCode) {
        ZimraFiscalisation fiscalisation = fiscalisationRepository.findByVerificationCode(verificationCode)
            .orElseThrow(() -> new RuntimeException("Fiscalisation not found: " + verificationCode));
        return mapper.toResponse(fiscalisation);
    }

    /**
     * Get fiscalisations by shop
     */
    @Transactional(readOnly = true)
    public Page<FiscalisationResponse> getByShop(Shop shop, Pageable pageable) {
        return fiscalisationRepository.findByShop(shop, pageable)
            .map(mapper::toResponse);
    }

    /**
     * Get fiscalisations by date range
     */
    @Transactional(readOnly = true)
    public List<FiscalisationResponse> getByDateRange(Shop shop, LocalDateTime startDate, LocalDateTime endDate) {
        return fiscalisationRepository.findByShopAndDateRange(shop, startDate, endDate)
            .stream()
            .map(mapper::toResponse)
            .toList();
    }

    /**
     * Get total fiscalised amount
     */
    @Transactional(readOnly = true)
    public Double getTotalFiscalisedAmount(Shop shop) {
        return fiscalisationRepository.getTotalFiscalisedAmountByShop(shop);
    }

    /**
     * Get fiscal device by ID
     */
    @Transactional(readOnly = true)
    public FiscalDeviceResponse getDevice(Long deviceId) {
        FiscalDevice device = deviceRepository.findById(deviceId)
            .orElseThrow(() -> new RuntimeException("Fiscal device not found: " + deviceId));
        return mapper.toDeviceResponse(device);
    }

    /**
     * Get all fiscal devices for a shop
     */
    @Transactional(readOnly = true)
    public List<FiscalDeviceResponse> getDevicesByShop(Shop shop) {
        return deviceRepository.findByShop(shop)
            .stream()
            .map(mapper::toDeviceResponse)
            .toList();
    }

    /**
     * Utility: Hash string using SHA-256
     */
    private String hashString(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Error hashing string", e);
        }
    }
}
