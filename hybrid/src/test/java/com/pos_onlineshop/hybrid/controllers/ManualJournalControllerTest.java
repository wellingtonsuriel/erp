package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.CreateManualJournalRequest;
import com.pos_onlineshop.hybrid.dtos.ManualJournalResponse;
import com.pos_onlineshop.hybrid.dtos.RejectManualJournalRequest;
import com.pos_onlineshop.hybrid.security.AuthenticatedActorResolver;
import com.pos_onlineshop.hybrid.services.ManualJournalService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ManualJournal's maker-checker rule (the preparer can never also approve their own journal)
 * only holds if createdBy/submittedBy/approvedBy/rejectedBy are the real authenticated identity -
 * previously all of them came from request-body fields (CreateManualJournalRequest.
 * createdByUserId, RejectManualJournalRequest.userId) that any caller with GL_APPROVE could set
 * to anything, defeating the check by simply claiming mismatched identities on each side. Those
 * userId fields have since been removed from the DTOs entirely; these tests confirm the
 * controller always resolves the real principal (via AuthenticatedActorResolver) rather than
 * trusting the body.
 */
@ExtendWith(MockitoExtension.class)
class ManualJournalControllerTest {

    @Mock private ManualJournalService manualJournalService;
    @Mock private AuthenticatedActorResolver actorResolver;

    private ManualJournalController controller;

    private UserDetails principal(String username) {
        return new User(username, "hashed", true, true, true, true, List.of());
    }

    @Test
    void createUsesTheAuthenticatedPrincipalAsCreatorNeverTheRequestBody() {
        controller = new ManualJournalController(manualJournalService, actorResolver);
        UserDetails principal = principal("real-clerk");
        when(actorResolver.requireActingUserId(principal)).thenReturn(11L);
        when(manualJournalService.create(any(), eq(11L)))
                .thenReturn(ManualJournalResponse.builder().id(1L).status("DRAFT").build());

        CreateManualJournalRequest request = new CreateManualJournalRequest();
        request.setCreatedByUserId(9999L); // attacker-controlled value that must be ignored

        ResponseEntity<?> response = controller.create(request, principal);

        verify(manualJournalService).create(request, 11L);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void approveUsesTheAuthenticatedPrincipalAsApproverNeverTheRequestBody() {
        controller = new ManualJournalController(manualJournalService, actorResolver);
        UserDetails principal = principal("real-manager");
        when(actorResolver.requireActingUserId(principal)).thenReturn(22L);
        when(manualJournalService.approve(5L, 22L))
                .thenReturn(ManualJournalResponse.builder().id(5L).status("APPROVED").build());

        ResponseEntity<?> response = controller.approve(5L, principal);

        verify(manualJournalService).approve(5L, 22L);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void rejectUsesTheAuthenticatedPrincipalAsRejecterNeverTheRequestBody() {
        controller = new ManualJournalController(manualJournalService, actorResolver);
        UserDetails principal = principal("real-manager");
        when(actorResolver.requireActingUserId(principal)).thenReturn(33L);
        when(manualJournalService.reject(5L, 33L, "Wrong period"))
                .thenReturn(ManualJournalResponse.builder().id(5L).status("REJECTED").build());

        RejectManualJournalRequest request = new RejectManualJournalRequest();
        request.setReason("Wrong period");

        ResponseEntity<?> response = controller.reject(5L, request, principal);

        verify(manualJournalService).reject(5L, 33L, "Wrong period");
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void postUsesTheAuthenticatedPrincipalAsPosterNeverTheRequestBody() {
        controller = new ManualJournalController(manualJournalService, actorResolver);
        UserDetails principal = principal("real-manager");
        when(actorResolver.requireActingUserId(principal)).thenReturn(44L);
        when(manualJournalService.post(5L, 44L))
                .thenReturn(ManualJournalResponse.builder().id(5L).status("POSTED").build());

        ResponseEntity<?> response = controller.post(5L, principal);

        verify(manualJournalService).post(5L, 44L);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void aCashierOnlyPrincipalCannotActOnManualJournalsAtAll() {
        controller = new ManualJournalController(manualJournalService, actorResolver);
        UserDetails principal = principal("cashier-admin");
        when(actorResolver.requireActingUserId(principal))
                .thenThrow(new IllegalStateException("Authenticated principal cashier-admin is not a UserAccount"));

        ResponseEntity<?> response = controller.approve(5L, principal);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}
