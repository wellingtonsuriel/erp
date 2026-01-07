package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.dtos.CreateCurrencyRequest;
import com.pos_onlineshop.hybrid.dtos.CreateExchangeRateRequest;
import com.pos_onlineshop.hybrid.dtos.UpdateCurrencyRequest;
import com.pos_onlineshop.hybrid.exchangeRate.ExchangeRate;
import com.pos_onlineshop.hybrid.services.CurrencyService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/currencies")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CurrencyController {

    private final CurrencyService currencyService;

    @GetMapping
    public List<Currency> getAllCurrencies() {
        return currencyService.findAllActive();
    }

    @GetMapping("/{code}")
    public ResponseEntity<Currency> getCurrencyByCode(@PathVariable String code) {
        return currencyService.findByCode(code)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/base")
    public ResponseEntity<Currency> getBaseCurrency() {
        return ResponseEntity.ok(currencyService.getBaseCurrency());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Currency> createCurrency(@RequestBody CreateCurrencyRequest request) {
        try {
            Currency currency = Currency.builder()
                    .code(request.getCode())
                    .name(request.getName())
                    .symbol(request.getSymbol())
                    .decimalPlaces(request.getDecimalPlaces())
                    .baseCurrency(request.isBaseCurrency())
                    .displayOrder(request.getDisplayOrder())
                    .build();

            Currency created = currencyService.createCurrency(currency);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Currency> updateCurrency(
            @PathVariable Long id,
            @RequestBody UpdateCurrencyRequest request) {
        try {
            Currency currencyDetails = Currency.builder()
                    .name(request.getName())
                    .symbol(request.getSymbol())
                    .decimalPlaces(request.getDecimalPlaces())
                    .active(request.isActive())
                    .displayOrder(request.getDisplayOrder())
                    .build();

            Currency updated = currencyService.updateCurrency(id, currencyDetails);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/exchange-rate")
    public ResponseEntity<BigDecimal> getExchangeRate(
            @RequestParam String from,
            @RequestParam String to) {
        try {
            Currency fromCurrency = currencyService.findByCode(from)
                    .orElseThrow(() -> new RuntimeException("From currency not found"));
            Currency toCurrency = currencyService.findByCode(to)
                    .orElseThrow(() -> new RuntimeException("To currency not found"));

            BigDecimal rate = currencyService.getExchangeRate(fromCurrency, toCurrency);
            return ResponseEntity.ok(rate);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/exchange-rates")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ExchangeRate> createExchangeRate(@RequestBody CreateExchangeRateRequest request) {
        try {
            Currency fromCurrency = currencyService.findByCode(request.getFromCurrencyCode())
                    .orElseThrow(() -> new RuntimeException("From currency not found"));
            Currency toCurrency = currencyService.findByCode(request.getToCurrencyCode())
                    .orElseThrow(() -> new RuntimeException("To currency not found"));

            ExchangeRate created = currencyService.createExchangeRate(
                    fromCurrency, toCurrency, request.getRate(),
                    request.getEffectiveDate(), request.getExpiryDate()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/convert")
    public ResponseEntity<Map<String, Object>> convertAmount(
            @RequestParam BigDecimal amount,
            @RequestParam String from,
            @RequestParam String to) {
        try {
            Currency fromCurrency = currencyService.findByCode(from)
                    .orElseThrow(() -> new RuntimeException("From currency not found"));
            Currency toCurrency = currencyService.findByCode(to)
                    .orElseThrow(() -> new RuntimeException("To currency not found"));

            BigDecimal convertedAmount = currencyService.convert(amount, fromCurrency, toCurrency);
            BigDecimal rate = currencyService.getExchangeRate(fromCurrency, toCurrency);

            return ResponseEntity.ok(Map.of(
                    "originalAmount", amount,
                    "fromCurrency", from,
                    "toCurrency", to,
                    "convertedAmount", convertedAmount,
                    "exchangeRate", rate
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }


}
