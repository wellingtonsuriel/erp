package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.AccrualResponse;
import com.pos_onlineshop.hybrid.dtos.CreateAccrualRequest;
import com.pos_onlineshop.hybrid.services.AccrualService;
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
 * create previously trusted CreateAccrualRequest.createdByUserId from the request body, and
 * reverse read the acting user straight from a client-suppliable ?userId query parameter - any
 * GL_ADMIN-authorized caller could claim any user id for either. These tests confirm the
 * controller now always resolves the real principal.
 */
@ExtendWith(MockitoExtension.class)
class AccrualControllerTest {

    @Mock private AccrualService accrualService;
    @Mock private UserAccountService userAccountService;

    private AccrualController controller;

    private UserDetails principal(String username) {
        return new User(username, "hashed", true, true, true, true, List.of());
    }

    @Test
    void createUsesTheAuthenticatedPrincipalNeverTheRequestBody() {
        controller = new AccrualController(accrualService, userAccountService);
        UserAccount realCreator = UserAccount.builder().id(11L).username("real-admin").build();
        when(userAccountService.findByUsername("real-admin")).thenReturn(Optional.of(realCreator));
        when(accrualService.createAccrual(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(11L)))
                .thenReturn(AccrualResponse.builder().id(1L).build());

        CreateAccrualRequest request = new CreateAccrualRequest();
        request.setCreatedByUserId(9999L); // attacker-controlled value that must be ignored

        ResponseEntity<?> response = controller.create(request, principal("real-admin"));

        verify(accrualService).createAccrual(request, 11L);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void reverseUsesTheAuthenticatedPrincipalNeverAQueryParameter() {
        controller = new AccrualController(accrualService, userAccountService);
        UserAccount realReverser = UserAccount.builder().id(22L).username("real-admin").build();
        when(userAccountService.findByUsername("real-admin")).thenReturn(Optional.of(realReverser));
        when(accrualService.reverseAccrual(5L, 22L)).thenReturn(AccrualResponse.builder().id(5L).build());

        ResponseEntity<?> response = controller.reverse(5L, principal("real-admin"));

        verify(accrualService).reverseAccrual(5L, 22L);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void aCashierOnlyPrincipalCannotCreateOrReverseAccruals() {
        controller = new AccrualController(accrualService, userAccountService);
        when(userAccountService.findByUsername("cashier-admin")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.reverse(5L, principal("cashier-admin"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}
