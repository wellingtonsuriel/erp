package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.accountancyEntry.AccountancyEntryRepository;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.orders.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountancyServiceTest {

    @Mock private AccountancyEntryRepository accountancyRepository;
    @Mock private CurrencyService currencyService;

    private AccountancyService service;
    private Currency usd;

    @BeforeEach
    void setUp() {
        service = new AccountancyService(accountancyRepository, currencyService);
        usd = Currency.builder().id(1L).code("USD").build();
    }

    private Order order() {
        return Order.builder().id(1L).currency(usd).totalAmount(new BigDecimal("100.00")).build();
    }

    @Test
    void createOrderAccountingEntriesWritesWhenDualWriteIsEnabledByDefault() {
        ReflectionTestUtils.setField(service, "legacyDualWriteEnabled", true);
        when(currencyService.getBaseCurrency()).thenReturn(usd);

        service.createOrderAccountingEntries(order());

        verify(accountancyRepository).saveAll(anyList());
    }

    @Test
    void createOrderAccountingEntriesIsANoOpWhenDualWriteIsDisabled() {
        ReflectionTestUtils.setField(service, "legacyDualWriteEnabled", false);

        service.createOrderAccountingEntries(order());

        verifyNoInteractions(accountancyRepository, currencyService);
    }

    @Test
    void createPaymentAccountingEntriesIsANoOpWhenDualWriteIsDisabled() {
        ReflectionTestUtils.setField(service, "legacyDualWriteEnabled", false);

        service.createPaymentAccountingEntries(order());

        verifyNoInteractions(accountancyRepository, currencyService);
    }

    @Test
    void createRefundAccountingEntriesIsANoOpWhenDualWriteIsDisabled() {
        ReflectionTestUtils.setField(service, "legacyDualWriteEnabled", false);

        service.createRefundAccountingEntries(order());

        verifyNoInteractions(accountancyRepository, currencyService);
    }
}
