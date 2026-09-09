package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.ApprovalDecisionRequest;
import com.pos_onlineshop.hybrid.dtos.ApprovalRequestResponse;
import com.pos_onlineshop.hybrid.dtos.CreateApprovalRequestRequest;
import com.pos_onlineshop.hybrid.services.UserAccountService;
import com.pos_onlineshop.hybrid.services.WorkflowService;
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
 * WorkflowService's maker-checker rule (a requester can never also decide their own request)
 * only holds if requestedBy/decidedBy are the real authenticated identity - previously both came
 * from request-body fields (CreateApprovalRequestRequest.requestedByUserId,
 * ApprovalDecisionRequest.userId) that any caller could set to anything, which let the check be
 * defeated by simply claiming mismatched identities on each side. These tests confirm the
 * controller now always resolves the real principal and ignores whatever the body claims.
 */
@ExtendWith(MockitoExtension.class)
class WorkflowControllerTest {

    @Mock private WorkflowService workflowService;
    @Mock private UserAccountService userAccountService;

    private WorkflowController controller;

    private UserDetails principal(String username) {
        return new User(username, "hashed", true, true, true, true, List.of());
    }

    @Test
    void createUsesTheAuthenticatedPrincipalAsRequesterNeverTheRequestBody() {
        controller = new WorkflowController(workflowService, userAccountService);
        UserAccount realRequester = UserAccount.builder().id(99L).username("real-clerk").build();
        when(userAccountService.findByUsername("real-clerk")).thenReturn(Optional.of(realRequester));
        when(workflowService.requestApproval(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(99L)))
                .thenReturn(ApprovalRequestResponse.builder().id(1L).status("PENDING").build());

        CreateApprovalRequestRequest request = new CreateApprovalRequestRequest();
        request.setEntityType("SELLING_PRICE");
        request.setEntityId(7L);
        request.setAction("PRICE_CHANGE");
        request.setRequestedByUserId(1234L); // attacker-controlled value that must be ignored

        controller.create(request, principal("real-clerk"));

        verify(workflowService).requestApproval(request, 99L);
    }

    @Test
    void approveUsesTheAuthenticatedPrincipalAsDeciderNeverTheRequestBody() {
        controller = new WorkflowController(workflowService, userAccountService);
        UserAccount realDecider = UserAccount.builder().id(55L).username("real-manager").build();
        when(userAccountService.findByUsername("real-manager")).thenReturn(Optional.of(realDecider));
        when(workflowService.approve(5L, 55L, "ok"))
                .thenReturn(ApprovalRequestResponse.builder().id(5L).status("APPROVED").build());

        ApprovalDecisionRequest request = new ApprovalDecisionRequest();
        request.setUserId(9999L); // attacker-controlled value that must be ignored
        request.setReason("ok");

        ResponseEntity<?> response = controller.approve(5L, request, principal("real-manager"));

        verify(workflowService).approve(5L, 55L, "ok");
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void aCashierOnlyPrincipalCannotActOnApprovalRequestsAtAll() {
        controller = new WorkflowController(workflowService, userAccountService);
        when(userAccountService.findByUsername("cashier-admin")).thenReturn(Optional.empty());

        ApprovalDecisionRequest request = new ApprovalDecisionRequest();
        request.setReason("ok");

        ResponseEntity<?> response = controller.approve(5L, request, principal("cashier-admin"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}
