package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.BankAccountResponse;
import com.pos_onlineshop.hybrid.dtos.BankChargeResponse;
import com.pos_onlineshop.hybrid.dtos.CashBankTransferResponse;
import com.pos_onlineshop.hybrid.dtos.CreateBankAccountRequest;
import com.pos_onlineshop.hybrid.dtos.CreateBankChargeRequest;
import com.pos_onlineshop.hybrid.dtos.CreateCashBankTransferRequest;
import com.pos_onlineshop.hybrid.services.BankAccountService;
import com.pos_onlineshop.hybrid.services.BankChargeService;
import com.pos_onlineshop.hybrid.services.CashBankTransferService;
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
 * createAccount/createTransfer/createCharge previously trusted a request-body createdByUserId -
 * any caller with the relevant GL authority could claim any user id. These tests confirm the
 * controller now always resolves the real principal.
 */
@ExtendWith(MockitoExtension.class)
class CashBankControllerTest {

    @Mock private BankAccountService bankAccountService;
    @Mock private CashBankTransferService cashBankTransferService;
    @Mock private BankChargeService bankChargeService;
    @Mock private UserAccountService userAccountService;

    private CashBankController controller;

    private UserDetails principal(String username) {
        return new User(username, "hashed", true, true, true, true, List.of());
    }

    @Test
    void createAccountUsesTheAuthenticatedPrincipalNeverTheRequestBody() {
        controller = new CashBankController(bankAccountService, cashBankTransferService, bankChargeService, userAccountService);
        UserAccount realCreator = UserAccount.builder().id(11L).username("real-admin").build();
        when(userAccountService.findByUsername("real-admin")).thenReturn(Optional.of(realCreator));
        when(bankAccountService.create(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(11L)))
                .thenReturn(BankAccountResponse.builder().id(1L).build());

        CreateBankAccountRequest request = new CreateBankAccountRequest();
        request.setCreatedByUserId(9999L);

        ResponseEntity<?> response = controller.createAccount(request, principal("real-admin"));

        verify(bankAccountService).create(request, 11L);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void createTransferUsesTheAuthenticatedPrincipalNeverTheRequestBody() {
        controller = new CashBankController(bankAccountService, cashBankTransferService, bankChargeService, userAccountService);
        UserAccount realCreator = UserAccount.builder().id(22L).username("real-clerk").build();
        when(userAccountService.findByUsername("real-clerk")).thenReturn(Optional.of(realCreator));
        when(cashBankTransferService.createTransfer(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(22L)))
                .thenReturn(CashBankTransferResponse.builder().id(1L).build());

        CreateCashBankTransferRequest request = new CreateCashBankTransferRequest();
        request.setCreatedByUserId(9999L);

        ResponseEntity<?> response = controller.createTransfer(request, principal("real-clerk"));

        verify(cashBankTransferService).createTransfer(request, 22L);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void createChargeUsesTheAuthenticatedPrincipalNeverTheRequestBody() {
        controller = new CashBankController(bankAccountService, cashBankTransferService, bankChargeService, userAccountService);
        UserAccount realCreator = UserAccount.builder().id(33L).username("real-clerk").build();
        when(userAccountService.findByUsername("real-clerk")).thenReturn(Optional.of(realCreator));
        when(bankChargeService.createCharge(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(33L)))
                .thenReturn(BankChargeResponse.builder().id(1L).build());

        CreateBankChargeRequest request = new CreateBankChargeRequest();
        request.setCreatedByUserId(9999L);

        ResponseEntity<?> response = controller.createCharge(request, principal("real-clerk"));

        verify(bankChargeService).createCharge(request, 33L);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void aCashierOnlyPrincipalCannotCreateABankAccount() {
        controller = new CashBankController(bankAccountService, cashBankTransferService, bankChargeService, userAccountService);
        when(userAccountService.findByUsername("cashier-admin")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.createAccount(new CreateBankAccountRequest(), principal("cashier-admin"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}
