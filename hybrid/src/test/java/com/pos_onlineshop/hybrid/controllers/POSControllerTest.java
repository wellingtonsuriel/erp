package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.cashier.Cashier;
import com.pos_onlineshop.hybrid.cashierSessions.CashierSession;
import com.pos_onlineshop.hybrid.dtos.QuickSaleRequest;
import com.pos_onlineshop.hybrid.dtos.VoidTransactionRequest;
import com.pos_onlineshop.hybrid.enums.CashierRole;
import com.pos_onlineshop.hybrid.enums.PaymentMethod;
import com.pos_onlineshop.hybrid.enums.Permission;
import com.pos_onlineshop.hybrid.orders.Order;
import com.pos_onlineshop.hybrid.services.CashierService;
import com.pos_onlineshop.hybrid.services.POSService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Covers the audit's P0-2 finding: quickSale/voidTransaction/openCashDrawer must resolve the
 * acting cashier from the authenticated principal, never from a client-supplied cashierId in
 * the request body - a request naming a different cashier's ID must not change whose session,
 * permissions, or identity the operation actually runs as.
 */
@ExtendWith(MockitoExtension.class)
class POSControllerTest {

    @Mock private POSService posService;
    @Mock private CashierService cashierService;

    private POSController controller;

    @BeforeEach
    void setUp() {
        controller = new POSController(posService, cashierService);
    }

    private Cashier cashier(long id, String username, CashierRole role) {
        return Cashier.builder().id(id).employeeId("EMP" + id).username(username).password("hashed")
                .firstName("A").lastName("B").email(username + "@shop.com").role(role).active(true).build();
    }

    private UserDetails principalFor(String username) {
        return new User(username, "hashed", true, true, true, true, Collections.emptyList());
    }

    @Test
    void quickSaleUsesTheSessionOfTheAuthenticatedCashierNotTheRequestBodyCashierId() {
        Cashier realCashier = cashier(5L, "realcashier", CashierRole.CASHIER);
        Cashier impersonatedCashier = cashier(999L, "victim", CashierRole.MANAGER);
        CashierSession realSession = CashierSession.builder().id(50L).cashier(realCashier).build();
        Order order = Order.builder().id(1L).totalAmount(new BigDecimal("10.00")).build();

        when(cashierService.findByUsername("realcashier")).thenReturn(Optional.of(realCashier));
        when(cashierService.getActiveSession(5L)).thenReturn(Optional.of(realSession));
        when(posService.processQuickSale(any(), any(), any(), eq(realSession))).thenReturn(order);

        QuickSaleRequest request = new QuickSaleRequest();
        request.setCashierId(999L); // attacker-controlled: claims to be a different cashier
        request.setItems(List.of());
        request.setPaymentMethod(PaymentMethod.CASH);
        request.setCashGiven(new BigDecimal("10.00"));

        ResponseEntity<Order> response = controller.quickSale(request, principalFor("realcashier"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        // The session looked up must be the AUTHENTICATED cashier's (5L), never the
        // request body's claimed cashierId (999L).
        verify(cashierService).getActiveSession(5L);
        verify(cashierService, never()).getActiveSession(999L);
        verify(cashierService, never()).findById(999L);
    }

    @Test
    void quickSaleFailsWhenThereIsNoAuthenticatedPrincipal() {
        QuickSaleRequest request = new QuickSaleRequest();
        request.setItems(List.of());
        request.setPaymentMethod(PaymentMethod.CASH);

        ResponseEntity<Order> response = controller.quickSale(request, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verifyNoInteractions(posService);
    }

    @Test
    void voidTransactionChecksPermissionOnTheAuthenticatedCashierNotTheRequestBodyCashierId() {
        Cashier realCashier = cashier(5L, "realcashier", CashierRole.MANAGER);
        when(cashierService.findByUsername("realcashier")).thenReturn(Optional.of(realCashier));
        when(cashierService.hasPermission(realCashier, Permission.VOID_TRANSACTION)).thenReturn(true);

        VoidTransactionRequest request = new VoidTransactionRequest();
        request.setCashierId(999L); // attacker-controlled, must be ignored
        request.setReason("Customer changed mind");

        ResponseEntity<Void> response = controller.voidTransaction(42L, request, principalFor("realcashier"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ArgumentCaptor<Cashier> captor = ArgumentCaptor.forClass(Cashier.class);
        verify(cashierService).hasPermission(captor.capture(), eq(Permission.VOID_TRANSACTION));
        assertEquals(5L, captor.getValue().getId());
        verify(posService).voidTransaction(42L, "Customer changed mind");
    }

    @Test
    void voidTransactionRejectsACashierWithoutThePermissionRegardlessOfRequestBody() {
        Cashier realCashier = cashier(5L, "junior", CashierRole.CASHIER);
        when(cashierService.findByUsername("junior")).thenReturn(Optional.of(realCashier));
        when(cashierService.hasPermission(realCashier, Permission.VOID_TRANSACTION)).thenReturn(false);

        VoidTransactionRequest request = new VoidTransactionRequest();
        request.setReason("trying to void anyway");

        ResponseEntity<Void> response = controller.voidTransaction(42L, request, principalFor("junior"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(posService, never()).voidTransaction(any(), any());
    }

    @Test
    void openCashDrawerResolvesPermissionFromTheAuthenticatedPrincipal() {
        Cashier realCashier = cashier(5L, "realcashier", CashierRole.CASHIER);
        when(cashierService.findByUsername("realcashier")).thenReturn(Optional.of(realCashier));
        when(cashierService.hasPermission(realCashier, Permission.OPEN_CASH_DRAWER)).thenReturn(true);

        ResponseEntity<Void> response = controller.openCashDrawer(principalFor("realcashier"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(posService).openCashDrawer();
    }

    @Test
    void requireAuthenticatedCashierThrowsWhenPrincipalIsNotACashier() {
        when(cashierService.findByUsername("onlineuser")).thenReturn(Optional.empty());

        QuickSaleRequest request = new QuickSaleRequest();
        request.setItems(List.of());
        request.setPaymentMethod(PaymentMethod.CASH);

        ResponseEntity<Order> response = controller.quickSale(request, principalFor("onlineuser"));

        // AccessDeniedException extends RuntimeException, caught by quickSale's existing
        // catch block - fails closed rather than proceeding.
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verifyNoInteractions(posService);
    }
}
