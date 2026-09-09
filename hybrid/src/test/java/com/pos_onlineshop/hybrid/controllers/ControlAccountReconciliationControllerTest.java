package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.ControlAccountReconciliationLineResponse;
import com.pos_onlineshop.hybrid.dtos.ControlAccountReconciliationRunResponse;
import com.pos_onlineshop.hybrid.dtos.ResolveReconciliationLineRequest;
import com.pos_onlineshop.hybrid.services.ControlAccountReconciliationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * run/resolveLine previously read performedBy/resolvedBy from a request-body field (defaulting
 * to "system" when absent) - any GL_ADMIN-authorized caller could claim any string, including
 * someone else's name. These tests confirm the controller now always uses the authenticated
 * principal's username.
 */
@ExtendWith(MockitoExtension.class)
class ControlAccountReconciliationControllerTest {

    @Mock private ControlAccountReconciliationService controlAccountReconciliationService;

    private ControlAccountReconciliationController controller;

    private UserDetails principal(String username) {
        return new User(username, "hashed", true, true, true, true, List.of());
    }

    @Test
    void runUsesTheAuthenticatedPrincipalsUsername() {
        controller = new ControlAccountReconciliationController(controlAccountReconciliationService);
        when(controlAccountReconciliationService.runAndPersist(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("real-admin")))
                .thenReturn(ControlAccountReconciliationRunResponse.builder().id(1L).build());

        controller.run(null, principal("real-admin"));

        verify(controlAccountReconciliationService).runAndPersist(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("real-admin"));
    }

    @Test
    void resolveLineUsesTheAuthenticatedPrincipalsUsernameNeverTheRequestBody() {
        controller = new ControlAccountReconciliationController(controlAccountReconciliationService);
        when(controlAccountReconciliationService.resolveLine(5L, "Investigated", "real-admin"))
                .thenReturn(ControlAccountReconciliationLineResponse.builder().id(5L).build());

        ResolveReconciliationLineRequest request = new ResolveReconciliationLineRequest();
        request.setResolutionReason("Investigated");
        request.setResolvedBy("attacker-controlled-value"); // must be ignored

        controller.resolveLine(5L, request, principal("real-admin"));

        verify(controlAccountReconciliationService).resolveLine(5L, "Investigated", "real-admin");
    }
}
