package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.currency.CurrencyRepository;
import com.pos_onlineshop.hybrid.dtos.CreateOpeningBalanceRequest;
import com.pos_onlineshop.hybrid.dtos.OpeningBalanceLineRequest;
import com.pos_onlineshop.hybrid.dtos.OpeningBalanceResponse;
import com.pos_onlineshop.hybrid.enums.AccountType;
import com.pos_onlineshop.hybrid.enums.DebitCredit;
import com.pos_onlineshop.hybrid.enums.GLSourceModule;
import com.pos_onlineshop.hybrid.gl.ManualLineSpec;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.journalLine.JournalLine;
import com.pos_onlineshop.hybrid.openingBalance.OpeningBalanceEntry;
import com.pos_onlineshop.hybrid.openingBalance.OpeningBalanceEntryRepository;
import com.pos_onlineshop.hybrid.shop.ShopRepository;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import com.pos_onlineshop.hybrid.userAccount.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OpeningBalanceServiceTest {

    @Mock private OpeningBalanceEntryRepository openingBalanceEntryRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private ShopRepository shopRepository;
    @Mock private UserAccountRepository userAccountRepository;
    @Mock private GLPostingService glPostingService;
    @Mock private CurrencyService currencyService;

    private OpeningBalanceService service;

    private UserAccount user;
    private Currency baseCurrency;
    private Account openingBalanceEquity;
    private Account accountsReceivable;
    private Account cash;

    @BeforeEach
    void setUp() {
        service = new OpeningBalanceService(openingBalanceEntryRepository, accountRepository, currencyRepository,
                shopRepository, userAccountRepository, glPostingService, currencyService);

        user = UserAccount.builder().id(1L).username("controller1").password("x").email("c@test.com").build();
        baseCurrency = Currency.builder().id(1L).code("USD").build();
        openingBalanceEquity = Account.builder().id(99L).code("3900").name("Opening Balance Equity")
                .accountType(AccountType.EQUITY).normalBalance(DebitCredit.CREDIT).controlAccount(false).active(true).build();
        accountsReceivable = Account.builder().id(2L).code("1100").name("Accounts Receivable")
                .accountType(AccountType.ASSET).normalBalance(DebitCredit.DEBIT).controlAccount(true).active(true).build();
        cash = Account.builder().id(3L).code("1000").name("Cash")
                .accountType(AccountType.ASSET).normalBalance(DebitCredit.DEBIT).controlAccount(false).active(true).build();

        lenient().when(currencyService.getBaseCurrency()).thenReturn(baseCurrency);
        lenient().when(accountRepository.findByCode("3900")).thenReturn(Optional.of(openingBalanceEquity));
        lenient().when(userAccountRepository.findById(1L)).thenReturn(Optional.of(user));
    }

    private CreateOpeningBalanceRequest requestWithOneLine(DebitCredit side, BigDecimal amount) {
        OpeningBalanceLineRequest line = new OpeningBalanceLineRequest();
        line.setAccountId(2L);
        line.setSide(side);
        line.setAmount(amount);
        CreateOpeningBalanceRequest request = new CreateOpeningBalanceRequest();
        request.setReference("OB-2026-001");
        request.setEntryDate(LocalDate.of(2026, 1, 1));
        request.setDescription("Go-live opening balances");
        request.setCreatedByUserId(1L);
        request.setLines(List.of(line));
        return request;
    }

    private JournalEntry postedEntryWithLines(List<ManualLineSpec> specs) {
        JournalEntry entry = JournalEntry.builder().id(500L).entryNumber(500L).build();
        for (ManualLineSpec spec : specs) {
            entry.addLine(JournalLine.builder().id((long) entry.getLines().size() + 1)
                    .account(spec.account()).debitAmount(spec.debitAmount()).creditAmount(spec.creditAmount())
                    .currency(spec.currency()).baseAmount(spec.debitAmount().max(spec.creditAmount()))
                    .exchangeRate(spec.exchangeRate()).costCenterShop(spec.costCenterShop()).memo(spec.memo()).build());
        }
        return entry;
    }

    @Test
    void createOpeningBalanceAppendsAnAutomaticBalancingLineAgainstOpeningBalanceEquity() {
        when(openingBalanceEntryRepository.existsByReference("OB-2026-001")).thenReturn(false);
        when(accountRepository.findById(2L)).thenReturn(Optional.of(accountsReceivable));
        when(openingBalanceEntryRepository.save(any(OpeningBalanceEntry.class))).thenAnswer(inv -> {
            OpeningBalanceEntry entry = inv.getArgument(0);
            if (entry.getId() == null) entry.setId(10L);
            return entry;
        });

        ArgumentCaptor<List<ManualLineSpec>> specsCaptor = ArgumentCaptor.forClass(List.class);
        when(glPostingService.postManual(eq("OPENING-BALANCE-OB-2026-001"), any(LocalDate.class), anyString(),
                eq(GLSourceModule.OPENING_BALANCE), eq("OPENING_BALANCE_ENTRY"), eq(10L), specsCaptor.capture(), eq("controller1")))
                .thenAnswer(inv -> postedEntryWithLines(specsCaptor.getValue()));

        OpeningBalanceResponse response = service.createOpeningBalance(
                requestWithOneLine(DebitCredit.DEBIT, new BigDecimal("500.00")));

        List<ManualLineSpec> specs = specsCaptor.getValue();
        assertEquals(2, specs.size());
        ManualLineSpec plug = specs.get(1);
        assertEquals(openingBalanceEquity, plug.account());
        assertEquals(0, new BigDecimal("500.00").compareTo(plug.creditAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(plug.debitAmount()));
        assertEquals(2, response.getLines().size());
        assertEquals(500L, response.getPostedJournalEntryId());
    }

    @Test
    void createOpeningBalanceSkipsThePlugLineWhenExplicitLinesAlreadyBalance() {
        OpeningBalanceLineRequest debitLine = new OpeningBalanceLineRequest();
        debitLine.setAccountId(3L);
        debitLine.setSide(DebitCredit.DEBIT);
        debitLine.setAmount(new BigDecimal("200.00"));
        OpeningBalanceLineRequest creditLine = new OpeningBalanceLineRequest();
        creditLine.setAccountId(2L);
        creditLine.setSide(DebitCredit.CREDIT);
        creditLine.setAmount(new BigDecimal("200.00"));
        CreateOpeningBalanceRequest request = new CreateOpeningBalanceRequest();
        request.setReference("OB-2026-002");
        request.setEntryDate(LocalDate.of(2026, 1, 1));
        request.setDescription("Balanced opening entry");
        request.setCreatedByUserId(1L);
        request.setLines(List.of(debitLine, creditLine));

        when(openingBalanceEntryRepository.existsByReference("OB-2026-002")).thenReturn(false);
        when(accountRepository.findById(3L)).thenReturn(Optional.of(cash));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(accountsReceivable));
        when(openingBalanceEntryRepository.save(any(OpeningBalanceEntry.class))).thenAnswer(inv -> {
            OpeningBalanceEntry entry = inv.getArgument(0);
            if (entry.getId() == null) entry.setId(11L);
            return entry;
        });

        ArgumentCaptor<List<ManualLineSpec>> specsCaptor = ArgumentCaptor.forClass(List.class);
        when(glPostingService.postManual(eq("OPENING-BALANCE-OB-2026-002"), any(LocalDate.class), anyString(),
                eq(GLSourceModule.OPENING_BALANCE), eq("OPENING_BALANCE_ENTRY"), eq(11L), specsCaptor.capture(), eq("controller1")))
                .thenAnswer(inv -> postedEntryWithLines(specsCaptor.getValue()));

        service.createOpeningBalance(request);

        assertEquals(2, specsCaptor.getValue().size());
        assertTrue(specsCaptor.getValue().stream().noneMatch(s -> s.account().equals(openingBalanceEquity)));
    }

    @Test
    void createOpeningBalanceRejectsADuplicateReference() {
        when(openingBalanceEntryRepository.existsByReference("OB-2026-001")).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> service.createOpeningBalance(requestWithOneLine(DebitCredit.DEBIT, new BigDecimal("500.00"))));
        verifyNoInteractions(glPostingService);
        verify(openingBalanceEntryRepository, never()).save(any());
    }

    @Test
    void createOpeningBalanceRejectsAnExplicitLineAgainstOpeningBalanceEquity() {
        OpeningBalanceLineRequest line = new OpeningBalanceLineRequest();
        line.setAccountId(99L);
        line.setSide(DebitCredit.CREDIT);
        line.setAmount(new BigDecimal("500.00"));
        CreateOpeningBalanceRequest request = new CreateOpeningBalanceRequest();
        request.setReference("OB-2026-003");
        request.setEntryDate(LocalDate.of(2026, 1, 1));
        request.setDescription("Invalid opening entry");
        request.setCreatedByUserId(1L);
        request.setLines(List.of(line));

        when(openingBalanceEntryRepository.existsByReference("OB-2026-003")).thenReturn(false);
        when(accountRepository.findById(99L)).thenReturn(Optional.of(openingBalanceEquity));

        assertThrows(IllegalArgumentException.class, () -> service.createOpeningBalance(request));
        verifyNoInteractions(glPostingService);
    }
}
