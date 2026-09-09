package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.cashier.Cashier;
import com.pos_onlineshop.hybrid.cashierSessions.CashierSession;
import com.pos_onlineshop.hybrid.dtos.AuthenticationRequest;
import com.pos_onlineshop.hybrid.dtos.EndSessionRequest;
import com.pos_onlineshop.hybrid.dtos.GrantPermissionRequest;
import com.pos_onlineshop.hybrid.dtos.StartSessionRequest;
import com.pos_onlineshop.hybrid.enums.CashierRole;
import com.pos_onlineshop.hybrid.enums.Permission;
import com.pos_onlineshop.hybrid.security.CashierUserDetailsService;
import com.pos_onlineshop.hybrid.security.JwtService;
import com.pos_onlineshop.hybrid.services.CashierService;
import com.pos_onlineshop.hybrid.services.ShopService;
import com.pos_onlineshop.hybrid.shop.Shop;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Covers several of the audit's P0/P1 authentication findings for this controller: (1)
 * authenticate / authenticate-by-pin must return a real, usable JWT - not silently omit the
 * field the frontend has always tried to read - (2) a permission grant's "grantedBy" must always
 * come from the authenticated caller, never a request-body field (GrantPermissionRequest.
 * grantedById exists but must never be trusted), (3) starting a shift session must always be
 * for the authenticated caller, never StartSessionRequest.cashierId (which previously let any
 * cashier-role holder open, and take financial responsibility for, ANY other cashier's drawer),
 * and (4) ending a session enforces the same real-identity rule via requireAuthenticatedCashier,
 * with the actual ownership check itself covered by CashierServiceEndSessionTest.
 */
@ExtendWith(MockitoExtension.class)
class CashierControllerTest {

    @Mock private CashierService cashierService;
    @Mock private ShopService shopService;
    @Mock private JwtService jwtService;
    @Mock private CashierUserDetailsService cashierUserDetailsService;

    private CashierController controller;

    @BeforeEach
    void setUp() {
        controller = new CashierController(cashierService, shopService, jwtService, cashierUserDetailsService);
    }

    private Cashier cashier(long id, String username, CashierRole role) {
        return Cashier.builder().id(id).employeeId("EMP" + id).username(username).password("hashed")
                .firstName("A").lastName("B").email(username + "@shop.com").role(role).active(true).build();
    }

    @Test
    void authenticateCashierReturnsARealJwtToken() {
        Cashier cashier = cashier(1L, "cashier1", CashierRole.CASHIER);
        when(cashierService.authenticateCashier("cashier1", "pw")).thenReturn(Optional.of(cashier));
        when(cashierService.getCashierPermissions(1L)).thenReturn(List.of(Permission.PROCESS_SALE));
        when(cashierUserDetailsService.authoritiesFor(cashier)).thenReturn(List.of());
        when(jwtService.generateToken(any())).thenReturn("signed.jwt.token");

        AuthenticationRequest request = new AuthenticationRequest();
        request.setUsername("cashier1");
        request.setPassword("pw");

        ResponseEntity<Map<String, Object>> response = controller.authenticateCashier(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("signed.jwt.token", response.getBody().get("token"));
        assertEquals("Bearer", response.getBody().get("tokenType"));
        assertEquals(true, response.getBody().get("success"));
    }

    @Test
    void authenticateCashierReturns401WithoutAnyTokenOnBadCredentials() {
        when(cashierService.authenticateCashier("cashier1", "wrong")).thenReturn(Optional.empty());

        AuthenticationRequest request = new AuthenticationRequest();
        request.setUsername("cashier1");
        request.setPassword("wrong");

        ResponseEntity<Map<String, Object>> response = controller.authenticateCashier(request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertFalse(response.getBody().containsKey("token"));
    }

    @Test
    void grantPermissionUsesTheAuthenticatedPrincipalAsGrantorNeverTheRequestBody() {
        Cashier target = cashier(2L, "cashier2", CashierRole.CASHIER);
        Cashier realGrantor = cashier(99L, "manager1", CashierRole.MANAGER);
        when(cashierService.findById(2L)).thenReturn(Optional.of(target));
        when(cashierService.findByUsername("manager1")).thenReturn(Optional.of(realGrantor));

        GrantPermissionRequest request = new GrantPermissionRequest();
        request.setPermission(Permission.OVERRIDE_PRICE);
        request.setGrantedById(1234L); // an attacker-controlled value that must be ignored

        UserDetails principal = new User("manager1", "hashed", true, true, true, true, List.of());
        ResponseEntity<Void> response = controller.grantPermission(2L, request, principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ArgumentCaptor<Cashier> grantorCaptor = ArgumentCaptor.forClass(Cashier.class);
        verify(cashierService).grantPermission(eq(target), eq(Permission.OVERRIDE_PRICE), grantorCaptor.capture());
        assertEquals(99L, grantorCaptor.getValue().getId());
        assertNotEquals(1234L, grantorCaptor.getValue().getId());
    }

    @Test
    void grantPermissionFailsClosedWhenTheAuthenticatedPrincipalIsNotACashier() {
        when(cashierService.findById(2L)).thenReturn(Optional.of(cashier(2L, "cashier2", CashierRole.CASHIER)));
        when(cashierService.findByUsername("someUserAccount")).thenReturn(Optional.empty());

        GrantPermissionRequest request = new GrantPermissionRequest();
        request.setPermission(Permission.OVERRIDE_PRICE);
        UserDetails principal = new User("someUserAccount", "hashed", true, true, true, true, List.of());

        ResponseEntity<Void> response = controller.grantPermission(2L, request, principal);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(cashierService, never()).grantPermission(any(), any(), any());
    }

    @Test
    void startSessionUsesTheAuthenticatedPrincipalAsTheCashierNeverTheRequestBody() {
        Cashier realCashier = cashier(1L, "real-cashier", CashierRole.CASHIER);
        Shop shop = Shop.builder().id(10L).build();
        when(cashierService.findByUsername("real-cashier")).thenReturn(Optional.of(realCashier));
        when(shopService.findById(10L)).thenReturn(Optional.of(shop));
        when(cashierService.startSession(eq(realCashier), eq(shop), eq("T1"), any(BigDecimal.class)))
                .thenReturn(CashierSession.builder().id(500L).build());

        StartSessionRequest request = new StartSessionRequest();
        request.setCashierId(9999L); // attacker-controlled value that must be ignored
        request.setShopId(10L);
        request.setTerminalId("T1");
        request.setOpeningCash(new BigDecimal("50"));

        UserDetails principal = new User("real-cashier", "hashed", true, true, true, true, List.of());
        ResponseEntity<CashierSession> response = controller.startSession(request, principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(cashierService).startSession(eq(realCashier), eq(shop), eq("T1"), any(BigDecimal.class));
        verify(cashierService, never()).findById(9999L);
    }

    @Test
    void endSessionResolvesTheActingCashierFromThePrincipalNotTheRequestBody() {
        Cashier realCashier = cashier(1L, "real-cashier", CashierRole.CASHIER);
        when(cashierService.findByUsername("real-cashier")).thenReturn(Optional.of(realCashier));
        when(cashierService.endSession(eq(500L), any(BigDecimal.class), anyString(), eq(realCashier)))
                .thenReturn(CashierSession.builder().id(500L).build());

        EndSessionRequest request = new EndSessionRequest();
        request.setClosingCash(new BigDecimal("120"));
        request.setNotes("end of shift");

        UserDetails principal = new User("real-cashier", "hashed", true, true, true, true, List.of());
        ResponseEntity<CashierSession> response = controller.endSession(500L, request, principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(cashierService).endSession(500L, new BigDecimal("120"), "end of shift", realCashier);
    }
}
