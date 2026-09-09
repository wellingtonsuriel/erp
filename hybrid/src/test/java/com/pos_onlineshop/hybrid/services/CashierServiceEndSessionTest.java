package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.cashier.Cashier;
import com.pos_onlineshop.hybrid.cashier.CashierRepository;
import com.pos_onlineshop.hybrid.cashierSessions.CashierSession;
import com.pos_onlineshop.hybrid.cashierSessions.CashierSessionRepository;
import com.pos_onlineshop.hybrid.enums.CashierRole;
import com.pos_onlineshop.hybrid.enums.SessionStatus;
import com.pos_onlineshop.hybrid.cashierPermission.CashierPermissionRepository;
import com.pos_onlineshop.hybrid.shop.ShopRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The audit's finding: CashierService.endSession had no check at all tying the caller to the
 * session being closed - any cashier-role holder could end ANY other cashier's session by ID
 * with a fabricated closingCash figure, which then posts a (fabricated) cash-difference entry to
 * the GL for a drawer they never actually held. A plain CASHIER may now only end their own
 * session; SUPERVISOR/MANAGER/ADMIN may end anyone's (reconciling a drawer left open).
 */
@ExtendWith(MockitoExtension.class)
class CashierServiceEndSessionTest {

    @Mock private CashierRepository cashierRepository;
    @Mock private CashierSessionRepository sessionRepository;
    @Mock private CashierPermissionRepository permissionRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ShopRepository shopRepository;
    @Mock private GLPostingService glPostingService;
    @Mock private CurrencyService currencyService;

    private CashierService service;

    @BeforeEach
    void setUp() {
        service = new CashierService(cashierRepository, sessionRepository, permissionRepository,
                passwordEncoder, shopRepository, glPostingService, currencyService);
    }

    private Cashier cashier(long id, CashierRole role) {
        return Cashier.builder().id(id).employeeId("EMP" + id).username("cashier" + id)
                .firstName("A").lastName("B").email("c" + id + "@shop.com").role(role).active(true).build();
    }

    private CashierSession sessionFor(Cashier owner) {
        return CashierSession.builder().id(500L).cashier(owner).status(SessionStatus.ACTIVE)
                .openingCash(new BigDecimal("100")).expectedCash(new BigDecimal("100")).build();
    }

    @Test
    void aCashierCanEndTheirOwnSession() {
        Cashier owner = cashier(1L, CashierRole.CASHIER);
        CashierSession session = sessionFor(owner);
        when(sessionRepository.findById(500L)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(CashierSession.class))).thenAnswer(inv -> inv.getArgument(0));

        CashierSession result = service.endSession(500L, new BigDecimal("100"), "end of shift", owner);

        assertEquals(SessionStatus.CLOSED, result.getStatus());
        verifyNoInteractions(glPostingService); // zero cash difference -> nothing to post
    }

    @Test
    void aCashierCannotEndAnotherCashiersSession() {
        Cashier owner = cashier(1L, CashierRole.CASHIER);
        Cashier otherCashier = cashier(2L, CashierRole.CASHIER);
        CashierSession session = sessionFor(owner);
        when(sessionRepository.findById(500L)).thenReturn(Optional.of(session));

        assertThrows(RuntimeException.class,
                () -> service.endSession(500L, new BigDecimal("100"), "not my drawer", otherCashier));

        verifyNoInteractions(glPostingService);
    }

    @Test
    void aSupervisorCanEndAnotherCashiersSession() {
        Cashier owner = cashier(1L, CashierRole.CASHIER);
        Cashier supervisor = cashier(3L, CashierRole.SUPERVISOR);
        CashierSession session = sessionFor(owner);
        when(sessionRepository.findById(500L)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(CashierSession.class))).thenAnswer(inv -> inv.getArgument(0));

        CashierSession result = service.endSession(500L, new BigDecimal("100"), "reconciling", supervisor);

        assertEquals(SessionStatus.CLOSED, result.getStatus());
    }
}
