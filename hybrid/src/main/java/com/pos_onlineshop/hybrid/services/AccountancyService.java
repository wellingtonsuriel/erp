package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.accountancyEntry.AccountancyEntry;
import com.pos_onlineshop.hybrid.accountancyEntry.AccountancyEntryRepository;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.enums.EntryType;
import com.pos_onlineshop.hybrid.orders.Order;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AccountancyService {

    private final AccountancyEntryRepository accountancyRepository;
    private final CurrencyService currencyService;

    public void createOrderAccountingEntries(Order order) {
        String description = String.format("Order #%d placed", order.getId());
        Currency baseCurrency = currencyService.getBaseCurrency();

        // Calculate base amount if order is in different currency
        BigDecimal baseAmount = order.getTotalAmount();
        BigDecimal exchangeRate = BigDecimal.ONE;

        if (!order.getCurrency().equals(baseCurrency)) {
            exchangeRate = currencyService.getExchangeRate(order.getCurrency(), baseCurrency);
            baseAmount = currencyService.convert(order.getTotalAmount(), order.getCurrency(), baseCurrency);
        }

        List<AccountancyEntry> entries = AccountancyEntry.createDoubleEntry(
                order.getTotalAmount(),
                order.getCurrency(),
                description,
                order.getUser(),
                "ORDER",
                order.getId(),
                baseAmount,
                exchangeRate
        );

        accountancyRepository.saveAll(entries);
        log.info("Created accounting entries for order {} in currency {}",
                order.getId(), order.getCurrency().getCode());
    }

    public void createPaymentAccountingEntries(Order order) {
        String description = String.format("Payment received for Order #%d", order.getId());
        Currency baseCurrency = currencyService.getBaseCurrency();

        BigDecimal baseAmount = order.getTotalAmount();
        BigDecimal exchangeRate = BigDecimal.ONE;

        if (!order.getCurrency().equals(baseCurrency)) {
            exchangeRate = currencyService.getExchangeRate(order.getCurrency(), baseCurrency);
            baseAmount = currencyService.convert(order.getTotalAmount(), order.getCurrency(), baseCurrency);
        }

        List<AccountancyEntry> entries = AccountancyEntry.createDoubleEntry(
                order.getTotalAmount(),
                order.getCurrency(),
                description,
                order.getUser(),
                "PAYMENT",
                order.getId(),
                baseAmount,
                exchangeRate
        );

        accountancyRepository.saveAll(entries);
        log.info("Created payment entries for order {} in currency {}",
                order.getId(), order.getCurrency().getCode());
    }

    public void createRefundAccountingEntries(Order order) {
        String description = String.format("Refund for Order #%d", order.getId());
        Currency baseCurrency = currencyService.getBaseCurrency();

        BigDecimal baseAmount = order.getTotalAmount();
        BigDecimal exchangeRate = BigDecimal.ONE;

        if (!order.getCurrency().equals(baseCurrency)) {
            exchangeRate = currencyService.getExchangeRate(order.getCurrency(), baseCurrency);
            baseAmount = currencyService.convert(order.getTotalAmount(), order.getCurrency(), baseCurrency);
        }

        List<AccountancyEntry> entries = AccountancyEntry.createDoubleEntry(
                order.getTotalAmount(),
                order.getCurrency(),
                description,
                order.getUser(),
                "REFUND",
                order.getId(),
                baseAmount,
                exchangeRate
        );

        accountancyRepository.saveAll(entries);
        log.info("Created refund entries for order {} in currency {}",
                order.getId(), order.getCurrency().getCode());
    }

    public AccountancyEntry createEntry(EntryType type, BigDecimal amount, Currency currency,
                                        String description, UserAccount user,
                                        String referenceType, Long referenceId) {
        Currency baseCurrency = currencyService.getBaseCurrency();
        BigDecimal baseAmount = amount;
        BigDecimal exchangeRate = BigDecimal.ONE;

        if (!currency.equals(baseCurrency)) {
            exchangeRate = currencyService.getExchangeRate(currency, baseCurrency);
            baseAmount = currencyService.convert(amount, currency, baseCurrency);
        }

        AccountancyEntry entry = AccountancyEntry.builder()
                .type(type)
                .amount(amount)
                .currency(currency)
                .description(description)
                .user(user)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .accountingPeriod(getCurrentPeriod())
                .baseAmount(baseAmount)
                .exchangeRate(exchangeRate)
                .build();

        return accountancyRepository.save(entry);
    }

    public Page<AccountancyEntry> findByUser(UserAccount user, Pageable pageable) {
        return accountancyRepository.findByUser(user, pageable);
    }

    public List<AccountancyEntry> findByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return accountancyRepository.findByEntryDateBetween(startDate, endDate);
    }

    public List<Object[]> getPeriodicSummary() {
        return accountancyRepository.getPeriodicSummary();
    }

    public BigDecimal getBalanceForPeriod(String period, Currency currency) {
        BigDecimal balance = accountancyRepository.calculateBalanceForPeriod(period);

        // Convert to requested currency if needed
        Currency baseCurrency = currencyService.getBaseCurrency();
        if (!currency.equals(baseCurrency) && balance != null) {
            balance = currencyService.convert(balance, baseCurrency, currency);
        }

        return balance;
    }

    public List<Object[]> getSummaryByType(EntryType type) {
        return accountancyRepository.getSummaryByReferenceType(type);
    }

    private String getCurrentPeriod() {
        LocalDateTime now = LocalDateTime.now();
        return String.format("%d-Q%d", now.getYear(), (now.getMonthValue() - 1) / 3 + 1);
    }
}
