package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.AccrualResponse;
import com.pos_onlineshop.hybrid.dtos.CreateAccrualRequest;
import com.pos_onlineshop.hybrid.security.AuthenticatedActorResolver;
import com.pos_onlineshop.hybrid.services.AccrualService;
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
 * create previously trusted CreateAccrualRequest.createdByUserId from the request body, and
 * reverse read the acting user straight from a client-suppliable ?userId query parameter - any
 * GL_ADMIN-authorized caller could claim any user id for either. These tests confirm the
 * controller now always resolves the real principal.
 */
@ExtendWith(MockitoExtension.class)
class AccrualControllerTest {

    @Mock private AccrualService accrualService;
    @Mock private AuthenticatedActorResolver actorResolver;

    private AccrualController controller;

    private UserDetails principal(String username) {
        return new User(username, "hashed", true, true, true, true, List.of());
    }

    @Test
    void createUsesTheAuthenticatedPrincipalNeverTheRequestBody() {
        controller = new AccrualController(accrualService, actorResolver);
        UserDetails principal = principal("real-admin");
        when(actorResolver.requireActingUserId(principal)).thenReturn(11L);
        when(accrualService.createAccrual(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(11L)))
                .thenReturn(AccrualResponse.builder().id(1L).build());

        CreateAccrualRequest request = new CreateAccrualRequest();
        request.setCreatedByUserId(9999L); // attacker-controlled value that must be ignored

        ResponseEntity<?> response = controller.create(request, principal);

        verify(accrualService).createAccrual(request, 11L);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void reverseUsesTheAuthenticatedPrincipalNeverAQueryParameter() {
        controller = new AccrualController(accrualService, actorResolver);
        UserDetails principal = principal("real-admin");
        when(actorResolver.requireActingUserId(principal)).thenReturn(22L);
        when(accrualService.reverseAccrual(5L, 22L)).thenReturn(AccrualResponse.builder().id(5L).build());

        ResponseEntity<?> response = controller.reverse(5L, principal);

        verify(accrualService).reverseAccrual(5L, 22L);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void aCashierOnlyPrincipalCannotCreateOrReverseAccruals() {
        controller = new AccrualController(accrualService, actorResolver);
        UserDetails principal = principal("cashier-admin");
        when(actorResolver.requireActingUserId(principal))
                .thenThrow(new IllegalStateException("Authenticated principal cashier-admin is not a UserAccount"));

        ResponseEntity<?> response = controller.reverse(5L, principal);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}
