package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.FiscalisationResponse;
import com.pos_onlineshop.hybrid.dtos.FiscalDeviceResponse;
import com.pos_onlineshop.hybrid.dtos.FiscaliseTransactionRequest;

import com.pos_onlineshop.hybrid.services.ZimraService;
import com.pos_onlineshop.hybrid.shop.Shop;
import com.pos_onlineshop.hybrid.shop.ShopRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/zimra")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "ZIMRA Fiscalisation", description = "ZIMRA fiscal compliance and device management")
public class ZimraController {

    private final ZimraService zimraService;
    private final ShopRepository shopRepository;

    @PostMapping("/fiscalise/order/{orderId}")
    @Operation(summary = "Fiscalise an order", description = "Send order to fiscal device for ZIMRA compliance")
    public ResponseEntity<FiscalisationResponse> fiscaliseOrder(
        @PathVariable Long orderId,
        @RequestBody FiscaliseTransactionRequest request
    ) {
        try {
            log.info("Fiscalising order: {}", orderId);
            FiscalisationResponse response = zimraService.fiscaliseOrder(orderId, request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fiscalising order {}", orderId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/fiscalise/sale/{saleId}")
    @Operation(summary = "Fiscalise a sale", description = "Send sale to fiscal device for ZIMRA compliance")
    public ResponseEntity<FiscalisationResponse> fiscaliseSale(
        @PathVariable Long saleId,
        @RequestBody FiscaliseTransactionRequest request
    ) {
        try {
            log.info("Fiscalising sale: {}", saleId);
            FiscalisationResponse response = zimraService.fiscaliseSale(saleId, request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fiscalising sale {}", saleId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/fiscal-code/{fiscalCode}")
    @Operation(summary = "Get fiscalisation by fiscal code", description = "Retrieve fiscal record by unique code")
    public ResponseEntity<FiscalisationResponse> getByFiscalCode(@PathVariable String fiscalCode) {
        try {
            FiscalisationResponse response = zimraService.getByFiscalCode(fiscalCode);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving fiscalisation {}", fiscalCode, e);
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/verification/{verificationCode}")
    @Operation(summary = "Verify fiscalisation", description = "Verify fiscal record using ZIMRA verification code")
    public ResponseEntity<FiscalisationResponse> verifyByCode(@PathVariable String verificationCode) {
        try {
            FiscalisationResponse response = zimraService.getByVerificationCode(verificationCode);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error verifying code {}", verificationCode, e);
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/shop/{shopId}")
    @Operation(summary = "Get shop fiscalisations", description = "Get all fiscalised transactions for a shop")
    public ResponseEntity<Page<FiscalisationResponse>> getByShop(
        @PathVariable Long shopId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        try {
            Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new RuntimeException("Shop not found"));
            Pageable pageable = PageRequest.of(page, size);
            Page<FiscalisationResponse> responses = zimraService.getByShop(shop, pageable);
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            log.error("Error retrieving fiscalisations for shop {}", shopId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/shop/{shopId}/date-range")
    @Operation(summary = "Get fiscalisations by date range", description = "Get fiscalised transactions within date range")
    public ResponseEntity<List<FiscalisationResponse>> getByDateRange(
        @PathVariable Long shopId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate
    ) {
        try {
            Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new RuntimeException("Shop not found"));
            List<FiscalisationResponse> responses = zimraService.getByDateRange(shop, startDate, endDate);
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            log.error("Error retrieving fiscalisations for date range", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/shop/{shopId}/total")
    @Operation(summary = "Get total fiscalised amount", description = "Get total amount of fiscalised transactions")
    public ResponseEntity<Double> getTotalFiscalised(@PathVariable Long shopId) {
        try {
            Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new RuntimeException("Shop not found"));
            Double total = zimraService.getTotalFiscalisedAmount(shop);
            return ResponseEntity.ok(total);
        } catch (Exception e) {
            log.error("Error calculating total fiscalised amount", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/retry-failed")
    @Operation(summary = "Retry failed fiscalisations", description = "Retry all failed fiscalisation attempts")
    public ResponseEntity<List<FiscalisationResponse>> retryFailed() {
        try {
            log.info("Retrying failed fiscalisations");
            List<FiscalisationResponse> responses = zimraService.retryFailedFiscalisations();
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            log.error("Error retrying failed fiscalisations", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/devices/{deviceId}")
    @Operation(summary = "Get fiscal device", description = "Get fiscal device details")
    public ResponseEntity<FiscalDeviceResponse> getDevice(@PathVariable Long deviceId) {
        try {
            FiscalDeviceResponse response = zimraService.getDevice(deviceId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving device {}", deviceId, e);
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/devices/shop/{shopId}")
    @Operation(summary = "Get shop devices", description = "Get all fiscal devices for a shop")
    public ResponseEntity<List<FiscalDeviceResponse>> getShopDevices(@PathVariable Long shopId) {
        try {
            Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new RuntimeException("Shop not found"));
            List<FiscalDeviceResponse> responses = zimraService.getDevicesByShop(shop);
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            log.error("Error retrieving devices for shop {}", shopId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
