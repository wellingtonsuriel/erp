package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.accountancyEntry.AccountancyEntry;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.dtos.CreateEntryRequest;
import com.pos_onlineshop.hybrid.enums.EntryType;
import com.pos_onlineshop.hybrid.services.AccountancyService;
import com.pos_onlineshop.hybrid.services.UserAccountService;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * createEntry's user attribution previously came from CreateEntryRequest.userId - a request-body
 * field any admin caller could set to attribute a ledger entry to a completely different user -
 * the same class of bug fixed on WorkflowController/PayrollController/SellingPriceController.
 */
@ExtendWith(MockitoExtension.class)
class AccountancyControllerTest {

    @Mock private AccountancyService accountancyService;
    @Mock private UserAccountService userAccountService;

    private AccountancyController controller;

    @Test
    void createEntryUsesTheAuthenticatedPrincipalNeverTheRequestBody() {
        controller = new AccountancyController(accountancyService, userAccountService);
        UserAccount realUser = UserAccount.builder().id(42L).username("real-admin").build();
        when(userAccountService.findByUsername("real-admin")).thenReturn(Optional.of(realUser));
        when(accountancyService.createEntry(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(AccountancyEntry.builder().id(1L).build());

        CreateEntryRequest request = new CreateEntryRequest();
        request.setType(EntryType.DEBIT);
        request.setAmount(new BigDecimal("100"));
        request.setCurrency(Currency.builder().id(1L).build());
        request.setDescription("test");
        request.setUserId(9999L); // attacker-controlled value that must be ignored

        UserDetails principal = new User("real-admin", "hashed", true, true, true, true, List.of());
        controller.createEntry(request, principal);

        ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(accountancyService).createEntry(eq(EntryType.DEBIT), eq(new BigDecimal("100")), any(),
                eq("test"), userCaptor.capture(), any(), any());
        assertEquals(42L, userCaptor.getValue().getId());
        assertNotEquals(9999L, userCaptor.getValue().getId());
    }
}
