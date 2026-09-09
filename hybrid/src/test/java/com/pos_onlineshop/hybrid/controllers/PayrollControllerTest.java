package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.PayrollRunResponse;
import com.pos_onlineshop.hybrid.dtos.ProcessPayrollRequest;
import com.pos_onlineshop.hybrid.enums.PaymentMethod;
import com.pos_onlineshop.hybrid.services.DeductionTypeService;
import com.pos_onlineshop.hybrid.services.PayrollService;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Both payRun's and processRun's acting-user audit attribution (who paid/created this run,
 * alongside the GL entries each posts) previously came from a request-body userId field any
 * caller could set to anything - the same class of bug fixed on
 * WorkflowController/SellingPriceController/AccountancyController. PayPayrollRunRequest, which
 * existed solely to carry that one now-dead field, was deleted rather than left as an empty
 * shell; ProcessPayrollRequest.userId is now ignored (kept, unvalidated, since the DTO has other
 * real fields).
 */
@ExtendWith(MockitoExtension.class)
class PayrollControllerTest {

    @Mock private PayrollService payrollService;
    @Mock private DeductionTypeService deductionTypeService;
    @Mock private UserAccountService userAccountService;

    private PayrollController controller;

    @Test
    void payRunUsesTheAuthenticatedPrincipalNeverTheRequestBody() {
        controller = new PayrollController(payrollService, deductionTypeService, userAccountService);
        UserAccount realPayer = UserAccount.builder().id(77L).username("real-admin").build();
        when(userAccountService.findByUsername("real-admin")).thenReturn(Optional.of(realPayer));
        when(payrollService.payRun(3L, 77L)).thenReturn(PayrollRunResponse.builder().id(3L).build());

        UserDetails principal = new User("real-admin", "hashed", true, true, true, true, List.of());
        ResponseEntity<?> response = controller.payRun(3L, principal);

        verify(payrollService).payRun(3L, 77L);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void payRunFailsClosedForANonUserAccountPrincipal() {
        controller = new PayrollController(payrollService, deductionTypeService, userAccountService);
        when(userAccountService.findByUsername("cashier-admin")).thenReturn(Optional.empty());

        UserDetails principal = new User("cashier-admin", "hashed", true, true, true, true, List.of());
        ResponseEntity<?> response = controller.payRun(3L, principal);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void processRunUsesTheAuthenticatedPrincipalAsCreatorNeverTheRequestBody() {
        controller = new PayrollController(payrollService, deductionTypeService, userAccountService);
        UserAccount realCreator = UserAccount.builder().id(88L).username("real-admin").build();
        when(userAccountService.findByUsername("real-admin")).thenReturn(Optional.of(realCreator));
        when(payrollService.processPayroll(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(88L)))
                .thenReturn(PayrollRunResponse.builder().id(9L).build());

        ProcessPayrollRequest request = new ProcessPayrollRequest();
        request.setRunNumber("PAY-1");
        request.setPeriodStart(LocalDate.of(2026, 1, 1));
        request.setPeriodEnd(LocalDate.of(2026, 1, 31));
        request.setPayDate(LocalDate.of(2026, 1, 31));
        request.setCurrencyId(1L);
        request.setPaymentMethod(PaymentMethod.CASH);

        UserDetails principal = new User("real-admin", "hashed", true, true, true, true, List.of());
        ResponseEntity<?> response = controller.processRun(request, principal);

        verify(payrollService).processPayroll(request, 88L);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }
}
