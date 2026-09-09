package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.CreateSalesReturnRequest;
import com.pos_onlineshop.hybrid.dtos.SalesReturnResponse;
import com.pos_onlineshop.hybrid.services.SalesReturnService;
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
 * create previously trusted CreateSalesReturnRequest.createdByUserId from the request body -
 * any caller with GL_MANUAL_JOURNAL could claim any user id. This test confirms the controller
 * now always resolves the real principal.
 */
@ExtendWith(MockitoExtension.class)
class SalesReturnControllerTest {

    @Mock private SalesReturnService salesReturnService;
    @Mock private UserAccountService userAccountService;

    private SalesReturnController controller;

    @Test
    void createUsesTheAuthenticatedPrincipalNeverTheRequestBody() {
        controller = new SalesReturnController(salesReturnService, userAccountService);
        UserAccount realCreator = UserAccount.builder().id(11L).username("real-clerk").build();
        when(userAccountService.findByUsername("real-clerk")).thenReturn(Optional.of(realCreator));
        when(salesReturnService.createReturn(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(11L)))
                .thenReturn(SalesReturnResponse.builder().id(1L).build());

        CreateSalesReturnRequest request = new CreateSalesReturnRequest();
        request.setCreatedByUserId(9999L); // attacker-controlled value that must be ignored

        UserDetails principal = new User("real-clerk", "hashed", true, true, true, true, List.of());
        ResponseEntity<?> response = controller.create(request, principal);

        verify(salesReturnService).createReturn(request, 11L);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }
}
