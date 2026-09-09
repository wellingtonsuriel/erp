package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.LoyaltyTransactionRequest;
import com.pos_onlineshop.hybrid.dtos.LoyaltyTransactionResponse;
import com.pos_onlineshop.hybrid.services.LoyaltyService;
import com.pos_onlineshop.hybrid.services.UserAccountService;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * earn/redeem/expire/reverse previously trusted LoyaltyTransactionRequest.createdByUserId from
 * the request body for who performed the transaction - any caller with GL_MANUAL_JOURNAL could
 * claim any user id. These tests confirm the controller now always resolves the real principal.
 */
@ExtendWith(MockitoExtension.class)
class LoyaltyControllerTest {

    @Mock private LoyaltyService loyaltyService;
    @Mock private UserAccountService userAccountService;

    private LoyaltyController controller;

    private UserDetails principal(String username) {
        return new User(username, "hashed", true, true, true, true, List.of());
    }

    @Test
    void earnUsesTheAuthenticatedPrincipalNeverTheRequestBody() {
        controller = new LoyaltyController(loyaltyService, userAccountService);
        UserAccount realActor = UserAccount.builder().id(11L).username("real-clerk").build();
        when(userAccountService.findByUsername("real-clerk")).thenReturn(Optional.of(realActor));
        when(loyaltyService.earn(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(11L)))
                .thenReturn(LoyaltyTransactionResponse.builder().id(1L).build());

        LoyaltyTransactionRequest request = new LoyaltyTransactionRequest();
        request.setCreatedByUserId(9999L); // attacker-controlled value that must be ignored

        ResponseEntity<?> response = controller.earn(request, principal("real-clerk"));

        verify(loyaltyService).earn(request, 11L);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void aCashierOnlyPrincipalCannotPerformLoyaltyTransactionsAtAll() {
        controller = new LoyaltyController(loyaltyService, userAccountService);
        when(userAccountService.findByUsername("cashier-admin")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.earn(new LoyaltyTransactionRequest(), principal("cashier-admin"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}
