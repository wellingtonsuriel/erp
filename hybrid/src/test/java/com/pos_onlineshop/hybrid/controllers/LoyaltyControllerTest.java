package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.LoyaltyTransactionRequest;
import com.pos_onlineshop.hybrid.dtos.LoyaltyTransactionResponse;
import com.pos_onlineshop.hybrid.security.AuthenticatedActorResolver;
import com.pos_onlineshop.hybrid.services.LoyaltyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

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
    @Mock private AuthenticatedActorResolver actorResolver;

    private LoyaltyController controller;

    private UserDetails principal(String username) {
        return new User(username, "hashed", true, true, true, true, List.of());
    }

    @Test
    void earnUsesTheAuthenticatedPrincipalNeverTheRequestBody() {
        controller = new LoyaltyController(loyaltyService, actorResolver);
        UserDetails principal = principal("real-clerk");
        when(actorResolver.requireActingUserId(principal)).thenReturn(11L);
        when(loyaltyService.earn(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(11L)))
                .thenReturn(LoyaltyTransactionResponse.builder().id(1L).build());

        LoyaltyTransactionRequest request = new LoyaltyTransactionRequest();
        request.setCreatedByUserId(9999L); // attacker-controlled value that must be ignored

        ResponseEntity<?> response = controller.earn(request, principal);

        verify(loyaltyService).earn(request, 11L);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void aCashierOnlyPrincipalCannotPerformLoyaltyTransactionsAtAll() {
        controller = new LoyaltyController(loyaltyService, actorResolver);
        UserDetails principal = principal("cashier-admin");
        when(actorResolver.requireActingUserId(principal))
                .thenThrow(new IllegalStateException("Authenticated principal cashier-admin is not a UserAccount"));

        ResponseEntity<?> response = controller.earn(new LoyaltyTransactionRequest(), principal);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}
