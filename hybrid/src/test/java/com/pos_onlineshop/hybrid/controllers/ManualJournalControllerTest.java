package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.CreateManualJournalRequest;
import com.pos_onlineshop.hybrid.dtos.ManualJournalResponse;
import com.pos_onlineshop.hybrid.dtos.RejectManualJournalRequest;
import com.pos_onlineshop.hybrid.services.ManualJournalService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ManualJournal's maker-checker rule (the preparer can never also approve their own journal)
 * only holds if createdBy/submittedBy/approvedBy/rejectedBy are the real authenticated identity -
 * previously all of them came from request-body fields (CreateManualJournalRequest.
 * createdByUserId, ManualJournalActionRequest/RejectManualJournalRequest.userId) that any caller
 * with GL_APPROVE could set to anything, defeating the check by simply claiming mismatched
 * identities on each side. These tests confirm the controller now always resolves the real
 * principal and ignores whatever the body claims.
 */
@ExtendWith(MockitoExtension.class)
class ManualJournalControllerTest {

    @Mock private ManualJournalService manualJournalService;
    @Mock private UserAccountService userAccountService;

    private ManualJournalController controller;

    private UserDetails principal(String username) {
        return new User(username, "hashed", true, true, true, true, List.of());
    }

    @Test
    void createUsesTheAuthenticatedPrincipalAsCreatorNeverTheRequestBody() {
        controller = new ManualJournalController(manualJournalService, userAccountService);
        UserAccount realCreator = UserAccount.builder().id(11L).username("real-clerk").build();
        when(userAccountService.findByUsername("real-clerk")).thenReturn(Optional.of(realCreator));
        when(manualJournalService.create(any(), eq(11L)))
                .thenReturn(ManualJournalResponse.builder().id(1L).status("DRAFT").build());

        CreateManualJournalRequest request = new CreateManualJournalRequest();
        request.setCreatedByUserId(9999L); // attacker-controlled value that must be ignored

        ResponseEntity<?> response = controller.create(request, principal("real-clerk"));

        verify(manualJournalService).create(request, 11L);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void approveUsesTheAuthenticatedPrincipalAsApproverNeverTheRequestBody() {
        controller = new ManualJournalController(manualJournalService, userAccountService);
        UserAccount realApprover = UserAccount.builder().id(22L).username("real-manager").build();
        when(userAccountService.findByUsername("real-manager")).thenReturn(Optional.of(realApprover));
        when(manualJournalService.approve(5L, 22L))
                .thenReturn(ManualJournalResponse.builder().id(5L).status("APPROVED").build());

        ResponseEntity<?> response = controller.approve(5L, principal("real-manager"));

        verify(manualJournalService).approve(5L, 22L);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void rejectUsesTheAuthenticatedPrincipalAsRejecterNeverTheRequestBody() {
        controller = new ManualJournalController(manualJournalService, userAccountService);
        UserAccount realRejecter = UserAccount.builder().id(33L).username("real-manager").build();
        when(userAccountService.findByUsername("real-manager")).thenReturn(Optional.of(realRejecter));
        when(manualJournalService.reject(5L, 33L, "Wrong period"))
                .thenReturn(ManualJournalResponse.builder().id(5L).status("REJECTED").build());

        RejectManualJournalRequest request = new RejectManualJournalRequest();
        request.setUserId(9999L); // attacker-controlled value that must be ignored
        request.setReason("Wrong period");

        ResponseEntity<?> response = controller.reject(5L, request, principal("real-manager"));

        verify(manualJournalService).reject(5L, 33L, "Wrong period");
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void postUsesTheAuthenticatedPrincipalAsPosterNeverTheRequestBody() {
        controller = new ManualJournalController(manualJournalService, userAccountService);
        UserAccount realPoster = UserAccount.builder().id(44L).username("real-manager").build();
        when(userAccountService.findByUsername("real-manager")).thenReturn(Optional.of(realPoster));
        when(manualJournalService.post(5L, 44L))
                .thenReturn(ManualJournalResponse.builder().id(5L).status("POSTED").build());

        ResponseEntity<?> response = controller.post(5L, principal("real-manager"));

        verify(manualJournalService).post(5L, 44L);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void aCashierOnlyPrincipalCannotActOnManualJournalsAtAll() {
        controller = new ManualJournalController(manualJournalService, userAccountService);
        when(userAccountService.findByUsername("cashier-admin")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.approve(5L, principal("cashier-admin"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}
