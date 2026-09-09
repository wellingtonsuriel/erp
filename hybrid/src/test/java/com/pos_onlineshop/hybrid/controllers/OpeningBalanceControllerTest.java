package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.CreateOpeningBalanceRequest;
import com.pos_onlineshop.hybrid.dtos.OpeningBalanceResponse;
import com.pos_onlineshop.hybrid.security.AuthenticatedActorResolver;
import com.pos_onlineshop.hybrid.services.OpeningBalanceService;
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
 * create previously trusted CreateOpeningBalanceRequest.createdByUserId from the request body -
 * any GL_ADMIN-authorized caller could claim any user id. This test confirms the controller now
 * always resolves the real principal.
 */
@ExtendWith(MockitoExtension.class)
class OpeningBalanceControllerTest {

    @Mock private OpeningBalanceService openingBalanceService;
    @Mock private AuthenticatedActorResolver actorResolver;

    private OpeningBalanceController controller;

    @Test
    void createUsesTheAuthenticatedPrincipalNeverTheRequestBody() {
        controller = new OpeningBalanceController(openingBalanceService, actorResolver);
        UserDetails principal = new User("real-admin", "hashed", true, true, true, true, List.of());
        when(actorResolver.requireActingUserId(principal)).thenReturn(11L);
        when(openingBalanceService.createOpeningBalance(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(11L)))
                .thenReturn(OpeningBalanceResponse.builder().id(1L).build());

        CreateOpeningBalanceRequest request = new CreateOpeningBalanceRequest();
        request.setCreatedByUserId(9999L); // attacker-controlled value that must be ignored

        ResponseEntity<?> response = controller.create(request, principal);

        verify(openingBalanceService).createOpeningBalance(request, 11L);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }
}
