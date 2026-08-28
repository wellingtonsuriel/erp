package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.dtos.PostingRuleLineRequest;
import com.pos_onlineshop.hybrid.dtos.PostingRuleRequest;
import com.pos_onlineshop.hybrid.dtos.PostingRuleResponse;
import com.pos_onlineshop.hybrid.enums.AccountType;
import com.pos_onlineshop.hybrid.enums.AmountSource;
import com.pos_onlineshop.hybrid.enums.DebitCredit;
import com.pos_onlineshop.hybrid.enums.FinancialEventType;
import com.pos_onlineshop.hybrid.postingRule.PostingRule;
import com.pos_onlineshop.hybrid.postingRule.PostingRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostingRuleServiceTest {

    @Mock private PostingRuleRepository postingRuleRepository;
    @Mock private AccountRepository accountRepository;

    private PostingRuleService service;
    private Account cashAccount;
    private Account revenueAccount;

    @BeforeEach
    void setUp() {
        service = new PostingRuleService(postingRuleRepository, accountRepository);
        cashAccount = Account.builder().id(1L).code("1010").name("Cash").accountType(AccountType.ASSET)
                .normalBalance(DebitCredit.DEBIT).build();
        revenueAccount = Account.builder().id(2L).code("4000").name("Revenue").accountType(AccountType.REVENUE)
                .normalBalance(DebitCredit.CREDIT).build();
    }

    private PostingRuleRequest request(FinancialEventType eventType) {
        PostingRuleLineRequest debitLine = new PostingRuleLineRequest();
        debitLine.setAccountId(1L);
        debitLine.setSide(DebitCredit.DEBIT);
        debitLine.setAmountSource(AmountSource.GROSS);
        debitLine.setSequence(1);

        PostingRuleLineRequest creditLine = new PostingRuleLineRequest();
        creditLine.setAccountId(2L);
        creditLine.setSide(DebitCredit.CREDIT);
        creditLine.setAmountSource(AmountSource.GROSS);
        creditLine.setSequence(2);

        PostingRuleRequest request = new PostingRuleRequest();
        request.setEventType(eventType);
        request.setDescription("Test rule");
        request.setActive(true);
        request.setLines(List.of(debitLine, creditLine));
        return request;
    }

    @Test
    void createRejectsDuplicateEventType() {
        when(postingRuleRepository.existsByEventType(FinancialEventType.MANUAL_ENTRY)).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.create(request(FinancialEventType.MANUAL_ENTRY)));
    }

    @Test
    void createBuildsRuleWithResolvedAccountsAndLines() {
        when(postingRuleRepository.existsByEventType(FinancialEventType.MANUAL_ENTRY)).thenReturn(false);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(cashAccount));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(revenueAccount));
        when(postingRuleRepository.save(any(PostingRule.class))).thenAnswer(inv -> inv.getArgument(0));

        PostingRuleResponse response = service.create(request(FinancialEventType.MANUAL_ENTRY));

        assertEquals(FinancialEventType.MANUAL_ENTRY, response.getEventType());
        assertEquals(2, response.getLines().size());
        assertEquals("1010", response.getLines().get(0).getAccountCode());
        assertEquals("4000", response.getLines().get(1).getAccountCode());
    }

    @Test
    void createThrowsWhenLineAccountDoesNotExist() {
        when(postingRuleRepository.existsByEventType(FinancialEventType.MANUAL_ENTRY)).thenReturn(false);
        when(accountRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.create(request(FinancialEventType.MANUAL_ENTRY)));
    }

    @Test
    void updateRejectsChangingEventType() {
        PostingRule existingRule = PostingRule.builder().id(5L).eventType(FinancialEventType.POS_CASH_SALE).active(true).build();
        when(postingRuleRepository.findById(5L)).thenReturn(Optional.of(existingRule));

        assertThrows(IllegalArgumentException.class,
                () -> service.update(5L, request(FinancialEventType.MANUAL_ENTRY)));
    }

    @Test
    void updateReplacesRuleLines() {
        PostingRule existingRule = PostingRule.builder().id(5L).eventType(FinancialEventType.MANUAL_ENTRY).active(true).build();
        when(postingRuleRepository.findById(5L)).thenReturn(Optional.of(existingRule));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(cashAccount));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(revenueAccount));
        when(postingRuleRepository.save(any(PostingRule.class))).thenAnswer(inv -> inv.getArgument(0));

        PostingRuleResponse response = service.update(5L, request(FinancialEventType.MANUAL_ENTRY));

        assertEquals(2, response.getLines().size());
    }

    @Test
    void activateAndDeactivateFlipTheActiveFlag() {
        PostingRule existingRule = PostingRule.builder().id(5L).eventType(FinancialEventType.MANUAL_ENTRY).active(false).build();
        when(postingRuleRepository.findById(5L)).thenReturn(Optional.of(existingRule));
        when(postingRuleRepository.save(any(PostingRule.class))).thenAnswer(inv -> inv.getArgument(0));

        assertTrue(service.activate(5L).isActive());
        assertFalse(service.deactivate(5L).isActive());
    }
}
