package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.customers.Customers;
import com.pos_onlineshop.hybrid.customers.CustomersRepository;
import com.pos_onlineshop.hybrid.dtos.LoyaltyAccountResponse;
import com.pos_onlineshop.hybrid.dtos.LoyaltyTransactionRequest;
import com.pos_onlineshop.hybrid.dtos.LoyaltyTransactionResponse;
import com.pos_onlineshop.hybrid.enums.AccountType;
import com.pos_onlineshop.hybrid.enums.DebitCredit;
import com.pos_onlineshop.hybrid.gl.FinancialEvent;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.loyaltyAccount.LoyaltyAccount;
import com.pos_onlineshop.hybrid.loyaltyAccount.LoyaltyAccountRepository;
import com.pos_onlineshop.hybrid.loyaltyTransaction.LoyaltyTransaction;
import com.pos_onlineshop.hybrid.loyaltyTransaction.LoyaltyTransactionRepository;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import com.pos_onlineshop.hybrid.userAccount.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoyaltyServiceTest {

    @Mock private LoyaltyAccountRepository loyaltyAccountRepository;
    @Mock private LoyaltyTransactionRepository loyaltyTransactionRepository;
    @Mock private CustomersRepository customersRepository;
    @Mock private UserAccountRepository userAccountRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private GLPostingService glPostingService;
    @Mock private CurrencyService currencyService;

    private LoyaltyService service;
    private Currency usd;
    private UserAccount clerk;
    private Customers customer;
    private Account loyaltyExpense;
    private Account loyaltyLiability;

    @BeforeEach
    void setUp() {
        service = new LoyaltyService(loyaltyAccountRepository, loyaltyTransactionRepository, customersRepository,
                userAccountRepository, accountRepository, glPostingService, currencyService);

        usd = Currency.builder().id(1L).code("USD").build();
        clerk = UserAccount.builder().id(1L).username("clerk1").password("x").email("clerk1@test.com").build();
        customer = Customers.builder().id(5L).name("Jane Doe").build();
        loyaltyExpense = Account.builder().id(1L).code("5600").name("Loyalty Program Expense")
                .accountType(AccountType.EXPENSE).normalBalance(DebitCredit.DEBIT).active(true).build();
        loyaltyLiability = Account.builder().id(2L).code("2300").name("Customer Deposits & Loyalty Liability")
                .accountType(AccountType.LIABILITY).normalBalance(DebitCredit.CREDIT).active(true).build();

        lenient().when(customersRepository.findById(5L)).thenReturn(Optional.of(customer));
        lenient().when(userAccountRepository.findById(1L)).thenReturn(Optional.of(clerk));
        lenient().when(accountRepository.findByCode("5600")).thenReturn(Optional.of(loyaltyExpense));
        lenient().when(accountRepository.findByCode("2300")).thenReturn(Optional.of(loyaltyLiability));
        lenient().when(currencyService.getBaseCurrency()).thenReturn(usd);
        lenient().when(loyaltyAccountRepository.save(any(LoyaltyAccount.class))).thenAnswer(inv -> {
            LoyaltyAccount a = inv.getArgument(0);
            if (a.getId() == null) a.setId(50L);
            return a;
        });
        lenient().when(loyaltyTransactionRepository.save(any(LoyaltyTransaction.class))).thenAnswer(inv -> {
            LoyaltyTransaction t = inv.getArgument(0);
            if (t.getId() == null) t.setId(700L);
            return t;
        });
    }

    private LoyaltyTransactionRequest request(BigDecimal amount) {
        LoyaltyTransactionRequest request = new LoyaltyTransactionRequest();
        request.setCustomerId(5L);
        request.setAmount(amount);
        request.setDescription("Order 100 loyalty");
        request.setTransactionDate(LocalDate.of(2026, 8, 15));
        request.setCreatedByUserId(1L);
        return request;
    }

    @Test
    void earnCreatesTheAccountOnFirstUseAndIncreasesBalance() {
        when(loyaltyAccountRepository.findByCustomerId(5L)).thenReturn(Optional.empty());
        JournalEntry entry = JournalEntry.builder().id(800L).entryNumber(80L).build();
        when(glPostingService.postManual(eq("LOYALTY-EARN-700"), eq(LocalDate.of(2026, 8, 15)), anyString(),
                any(), eq("LOYALTY_TRANSACTION"), eq(700L), anyList(), eq("clerk1")))
                .thenReturn(entry);

        LoyaltyTransactionResponse response = service.earn(request(new BigDecimal("10.00")));

        assertEquals("EARNED", response.getTransactionType());
        assertEquals(0, new BigDecimal("10.00").compareTo(response.getBalanceAfter()));
    }

    @Test
    void redeemRejectsWhenNoAccountExists() {
        when(loyaltyAccountRepository.findByCustomerId(5L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.redeem(request(new BigDecimal("5.00"))));
    }

    @Test
    void redeemRejectsRedeemingMoreThanIsAvailable() {
        LoyaltyAccount account = LoyaltyAccount.builder().id(50L).customer(customer).availableBalance(new BigDecimal("5.00")).build();
        when(loyaltyAccountRepository.findByCustomerId(5L)).thenReturn(Optional.of(account));

        assertThrows(IllegalStateException.class, () -> service.redeem(request(new BigDecimal("10.00"))));
        verifyNoInteractions(glPostingService);
    }

    @Test
    void redeemPostsViaTheLoyaltyRedemptionFinancialEventAndDecreasesBalance() {
        LoyaltyAccount account = LoyaltyAccount.builder().id(50L).customer(customer).availableBalance(new BigDecimal("15.00")).build();
        when(loyaltyAccountRepository.findByCustomerId(5L)).thenReturn(Optional.of(account));
        JournalEntry entry = JournalEntry.builder().id(801L).entryNumber(81L).build();
        when(glPostingService.post(any(FinancialEvent.class))).thenReturn(entry);

        LoyaltyTransactionResponse response = service.redeem(request(new BigDecimal("10.00")));

        assertEquals("REDEEMED", response.getTransactionType());
        assertEquals(0, new BigDecimal("5.00").compareTo(response.getBalanceAfter()));
        assertEquals(0, new BigDecimal("5.00").compareTo(account.getAvailableBalance()));
    }

    @Test
    void expireRejectsExpiringMoreThanIsAvailable() {
        LoyaltyAccount account = LoyaltyAccount.builder().id(50L).customer(customer).availableBalance(new BigDecimal("2.00")).build();
        when(loyaltyAccountRepository.findByCustomerId(5L)).thenReturn(Optional.of(account));

        assertThrows(IllegalStateException.class, () -> service.expire(request(new BigDecimal("3.00"))));
    }

    @Test
    void getAccountReturnsAZeroBalanceForACustomerWithNoLoyaltyActivityYet() {
        when(loyaltyAccountRepository.findByCustomerId(5L)).thenReturn(Optional.empty());

        LoyaltyAccountResponse response = service.getAccount(5L);

        assertEquals(0, BigDecimal.ZERO.compareTo(response.getAvailableBalance()));
    }
}
