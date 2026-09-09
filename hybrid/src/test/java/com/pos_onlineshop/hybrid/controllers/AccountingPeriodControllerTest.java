package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.accountingPeriod.AccountingPeriod;
import com.pos_onlineshop.hybrid.services.AccountingPeriodService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * close/reopen's closedBy/reopenedBy previously came from a request-body field
 * (Map<String,String> body, defaulting to "system" when absent) that any GL_ADMIN-authorized
 * caller could set to any string, forging who closed or reopened a period in both the period's
 * own audit column and every GL entry the close posts. These tests confirm the controller now
 * always uses the authenticated principal's username instead.
 */
@ExtendWith(MockitoExtension.class)
class AccountingPeriodControllerTest {

    @Mock private AccountingPeriodService accountingPeriodService;

    private AccountingPeriodController controller;

    private UserDetails principal(String username) {
        return new User(username, "hashed", true, true, true, true, List.of());
    }

    @Test
    void closeUsesTheAuthenticatedPrincipalsUsernameNeverTheRequestBody() {
        controller = new AccountingPeriodController(accountingPeriodService);
        when(accountingPeriodService.closePeriod(5L, "real-admin"))
                .thenReturn(AccountingPeriod.builder().id(5L).build());

        controller.close(5L, principal("real-admin"));

        verify(accountingPeriodService).closePeriod(5L, "real-admin");
    }

    @Test
    void reopenUsesTheAuthenticatedPrincipalsUsernameNeverTheRequestBody() {
        controller = new AccountingPeriodController(accountingPeriodService);
        when(accountingPeriodService.reopenPeriod(5L, "real-admin"))
                .thenReturn(AccountingPeriod.builder().id(5L).build());

        controller.reopen(5L, principal("real-admin"));

        verify(accountingPeriodService).reopenPeriod(5L, "real-admin");
    }
}
