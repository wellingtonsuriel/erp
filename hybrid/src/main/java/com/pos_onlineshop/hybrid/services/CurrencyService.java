package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.currency.CurrencyRepository;
import com.pos_onlineshop.hybrid.exchangeRate.ExchangeRate;
import com.pos_onlineshop.hybrid.exchangeRate.ExchangeRateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CurrencyService {

    private final CurrencyRepository currencyRepository;
    private final ExchangeRateRepository exchangeRateRepository;

    public Currency createCurrency(Currency currency) {
        if (currencyRepository.existsByCode(currency.getCode())) {
            throw new RuntimeException("Currency already exists: " + currency.getCode());
        }

        // Only one base currency allowed
        if (currency.isBaseCurrency()) {
            currencyRepository.findByBaseCurrencyTrue()
                    .ifPresent(existing -> {
                        existing.setBaseCurrency(false);
                        currencyRepository.save(existing);
                    });
        }

        return currencyRepository.save(currency);
    }

    // Add these methods to CurrencyService.java

    /**
     * Get default currency (fallback if base currency not found)
     */
    @Cacheable("defaultCurrency")
    public Currency getDefaultCurrency() {
        // First try to get base currency
        Optional<Currency> baseCurrency = currencyRepository.findByBaseCurrencyTrue();
        if (baseCurrency.isPresent()) {
            return baseCurrency.get();
        }

        // Fallback to USD if no base currency is set
        Optional<Currency> usdCurrency = currencyRepository.findByCode("USD");
        if (usdCurrency.isPresent()) {
            return usdCurrency.get();
        }

        // If no USD, get the first active currency
        List<Currency> activeCurrencies = currencyRepository.findAllActiveOrdered();
        if (!activeCurrencies.isEmpty()) {
            return activeCurrencies.get(0);
        }

        // If no currencies exist, throw exception
        throw new RuntimeException("No currencies configured in the system");
    }

    @Cacheable("currencies")
    public Optional<Currency> findByCode(String code) {
        return currencyRepository.findByCode(code);
    }

    @Cacheable("baseCurrency")
    public Currency getBaseCurrency() {
        return currencyRepository.findByBaseCurrencyTrue()
                .orElseThrow(() -> new RuntimeException("No base currency configured"));
    }

    public List<Currency> findAllActive() {
        return currencyRepository.findAllActiveOrdered();
    }

    public Currency updateCurrency(Long id, Currency currencyDetails) {
        return currencyRepository.findById(id)
                .map(currency -> {
                    currency.setName(currencyDetails.getName());
                    currency.setSymbol(currencyDetails.getSymbol());
                    currency.setDecimalPlaces(currencyDetails.getDecimalPlaces());
                    currency.setActive(currencyDetails.isActive());
                    currency.setDisplayOrder(currencyDetails.getDisplayOrder());
                    return currencyRepository.save(currency);
                })
                .orElseThrow(() -> new RuntimeException("Currency not found"));
    }

    // Exchange rate management
    public ExchangeRate createExchangeRate(Currency from, Currency to, BigDecimal rate,
                                           LocalDateTime effectiveDate, LocalDateTime expiryDate) {
        // Deactivate existing rates for this currency pair
        exchangeRateRepository.findCurrentRate(from, to, LocalDateTime.now())
                .ifPresent(existing -> {
                    existing.setActive(false);
                    exchangeRateRepository.save(existing);
                });

        ExchangeRate exchangeRate = ExchangeRate.builder()
                .fromCurrency(from)
                .toCurrency(to)
                .rate(rate)
                .effectiveDate(effectiveDate)
                .expiryDate(expiryDate)
                .source("MANUAL")
                .build();

        return exchangeRateRepository.save(exchangeRate);
    }

    @Cacheable("exchangeRates")
    public BigDecimal getExchangeRate(Currency from, Currency to) {
        if (from.equals(to)) {
            return BigDecimal.ONE;
        }

        // Direct rate
        Optional<ExchangeRate> directRate = exchangeRateRepository
                .findCurrentRate(from, to, LocalDateTime.now());
        if (directRate.isPresent()) {
            return directRate.get().getRate();
        }

        // Inverse rate
        Optional<ExchangeRate> inverseRate = exchangeRateRepository
                .findCurrentRate(to, from, LocalDateTime.now());
        if (inverseRate.isPresent()) {
            return BigDecimal.ONE.divide(inverseRate.get().getRate(), 6, RoundingMode.HALF_UP);
        }

        // Cross rate through base currency
        Currency baseCurrency = getBaseCurrency();
        if (!from.equals(baseCurrency) && !to.equals(baseCurrency)) {
            BigDecimal fromToBase = getExchangeRate(from, baseCurrency);
            BigDecimal baseToTo = getExchangeRate(baseCurrency, to);
            return fromToBase.multiply(baseToTo);
        }

        throw new RuntimeException(String.format("No exchange rate found from %s to %s",
                from.getCode(), to.getCode()));
    }

    public BigDecimal convert(BigDecimal amount, Currency from, Currency to) {
        if (from.equals(to)) {
            return amount;
        }

        BigDecimal rate = getExchangeRate(from, to);
        return amount.multiply(rate).setScale(to.getDecimalPlaces(), RoundingMode.HALF_UP);
    }

    public List<ExchangeRate> getExchangeRatesForCurrency(Currency currency) {
        return exchangeRateRepository.findByFromCurrencyAndActiveTrue(currency);
    }

    public void updateExchangeRates(List<ExchangeRate> rates) {
        // Bulk update exchange rates (e.g., from external API)
        exchangeRateRepository.saveAll(rates);
        log.info("Updated {} exchange rates", rates.size());
    }
}
