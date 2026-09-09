package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.BankAccountResponse;
import com.pos_onlineshop.hybrid.dtos.BankChargeResponse;
import com.pos_onlineshop.hybrid.dtos.CashBankTransferResponse;
import com.pos_onlineshop.hybrid.dtos.CreateBankAccountRequest;
import com.pos_onlineshop.hybrid.dtos.CreateBankChargeRequest;
import com.pos_onlineshop.hybrid.dtos.CreateCashBankTransferRequest;
import com.pos_onlineshop.hybrid.security.AuthenticatedActorResolver;
import com.pos_onlineshop.hybrid.services.BankAccountService;
import com.pos_onlineshop.hybrid.services.BankChargeService;
import com.pos_onlineshop.hybrid.services.CashBankTransferService;
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
 * createAccount/createTransfer/createCharge previously trusted a request-body createdByUserId -
 * any caller with the relevant GL authority could claim any user id. These tests confirm the
 * controller now always resolves the real principal.
 */
@ExtendWith(MockitoExtension.class)
class CashBankControllerTest {

    @Mock private BankAccountService bankAccountService;
    @Mock private CashBankTransferService cashBankTransferService;
    @Mock private BankChargeService bankChargeService;
    @Mock private AuthenticatedActorResolver actorResolver;

    private CashBankController controller;

    private UserDetails principal(String username) {
        return new User(username, "hashed", true, true, true, true, List.of());
    }

    @Test
    void createAccountUsesTheAuthenticatedPrincipalNeverTheRequestBody() {
        controller = new CashBankController(bankAccountService, cashBankTransferService, bankChargeService, actorResolver);
        UserDetails principal = principal("real-admin");
        when(actorResolver.requireActingUserId(principal)).thenReturn(11L);
        when(bankAccountService.create(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(11L)))
                .thenReturn(BankAccountResponse.builder().id(1L).build());

        CreateBankAccountRequest request = new CreateBankAccountRequest();
        request.setCreatedByUserId(9999L);

        ResponseEntity<?> response = controller.createAccount(request, principal);

        verify(bankAccountService).create(request, 11L);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void createTransferUsesTheAuthenticatedPrincipalNeverTheRequestBody() {
        controller = new CashBankController(bankAccountService, cashBankTransferService, bankChargeService, actorResolver);
        UserDetails principal = principal("real-clerk");
        when(actorResolver.requireActingUserId(principal)).thenReturn(22L);
        when(cashBankTransferService.createTransfer(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(22L)))
                .thenReturn(CashBankTransferResponse.builder().id(1L).build());

        CreateCashBankTransferRequest request = new CreateCashBankTransferRequest();
        request.setCreatedByUserId(9999L);

        ResponseEntity<?> response = controller.createTransfer(request, principal);

        verify(cashBankTransferService).createTransfer(request, 22L);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void createChargeUsesTheAuthenticatedPrincipalNeverTheRequestBody() {
        controller = new CashBankController(bankAccountService, cashBankTransferService, bankChargeService, actorResolver);
        UserDetails principal = principal("real-clerk");
        when(actorResolver.requireActingUserId(principal)).thenReturn(33L);
        when(bankChargeService.createCharge(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(33L)))
                .thenReturn(BankChargeResponse.builder().id(1L).build());

        CreateBankChargeRequest request = new CreateBankChargeRequest();
        request.setCreatedByUserId(9999L);

        ResponseEntity<?> response = controller.createCharge(request, principal);

        verify(bankChargeService).createCharge(request, 33L);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void aCashierOnlyPrincipalCannotCreateABankAccount() {
        controller = new CashBankController(bankAccountService, cashBankTransferService, bankChargeService, actorResolver);
        UserDetails principal = principal("cashier-admin");
        when(actorResolver.requireActingUserId(principal))
                .thenThrow(new IllegalStateException("Authenticated principal cashier-admin is not a UserAccount"));

        ResponseEntity<?> response = controller.createAccount(new CreateBankAccountRequest(), principal);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}
