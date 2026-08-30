package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.CreateManualJournalRequest;
import com.pos_onlineshop.hybrid.dtos.ManualJournalActionRequest;
import com.pos_onlineshop.hybrid.dtos.ManualJournalResponse;
import com.pos_onlineshop.hybrid.dtos.RejectManualJournalRequest;
import com.pos_onlineshop.hybrid.security.AuthenticatedActorResolver;
import com.pos_onlineshop.hybrid.services.ManualJournalService;
import com.pos_onlineshop.hybrid.services.UserAccountService;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression test for a real impersonation hole: every maker-checker action here (submit,
 * approve, reject, post, and even create's preparer id) used to trust whatever "userId"/
 * "createdByUserId" the client put in the request body. Since ManualJournal.approve() and
 * reject() enforce "the preparer cannot approve their own journal" purely by comparing that
 * field against the stored preparer, a caller with a valid token for their own account could
 * simply put a *different* account's id in the body and approve their own journal under a
 * forged identity. The fix resolves the acting user from the JWT-authenticated principal via
 * AuthenticatedActorResolver and overwrites whatever the client sent - these tests prove the
 * forged value in the request body never reaches the service.
 */
@ExtendWith(MockitoExtension.class)
class ManualJournalControllerActorSecurityTest {

    @Mock private ManualJournalService manualJournalService;
    @Mock private UserAccountService userAccountService;

    private ManualJournalController controller;

    private static final long REAL_AUTHENTICATED_USER_ID = 7L;
    private static final long FORGED_USER_ID = 999L;
    private static final UserDetails PRINCIPAL = new User("real.user", "hashed", List.of());

    @BeforeEach
    void setUp() {
        controller = new ManualJournalController(manualJournalService, new AuthenticatedActorResolver(userAccountService));
    }

    private void stubRealAuthenticatedUser() {
        UserAccount realAccount = new UserAccount();
        realAccount.setId(REAL_AUTHENTICATED_USER_ID);
        when(userAccountService.findByUsername("real.user")).thenReturn(Optional.of(realAccount));
    }

    @Test
    void createIgnoresAClientSuppliedPreparerIdAndUsesTheAuthenticatedPrincipal() {
        stubRealAuthenticatedUser();
        CreateManualJournalRequest request = new CreateManualJournalRequest();
        request.setEntryDate(LocalDate.now());
        request.setDescription("test");
        request.setCreatedByUserId(FORGED_USER_ID);
        request.setLines(List.of());
        when(manualJournalService.create(any())).thenReturn(ManualJournalResponse.builder().id(1L).build());

        controller.create(request, PRINCIPAL);

        assertEquals(REAL_AUTHENTICATED_USER_ID, request.getCreatedByUserId(),
                "the forged createdByUserId must be overwritten with the real authenticated user's id");
    }

    @Test
    void approveIgnoresAClientSuppliedUserIdAndUsesTheAuthenticatedPrincipal() {
        stubRealAuthenticatedUser();
        ManualJournalActionRequest request = new ManualJournalActionRequest();
        request.setUserId(FORGED_USER_ID);
        when(manualJournalService.approve(eq(5L), any())).thenReturn(ManualJournalResponse.builder().id(5L).build());

        ResponseEntity<?> response = controller.approve(5L, request, PRINCIPAL);

        ArgumentCaptor<ManualJournalActionRequest> captor = ArgumentCaptor.forClass(ManualJournalActionRequest.class);
        verify(manualJournalService).approve(eq(5L), captor.capture());
        assertEquals(REAL_AUTHENTICATED_USER_ID, captor.getValue().getUserId(),
                "the service must see the real approver id, not the forged one from the request body");
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void rejectIgnoresAClientSuppliedUserIdAndUsesTheAuthenticatedPrincipal() {
        stubRealAuthenticatedUser();
        RejectManualJournalRequest request = new RejectManualJournalRequest();
        request.setUserId(FORGED_USER_ID);
        request.setReason("not needed");
        when(manualJournalService.reject(eq(5L), any())).thenReturn(ManualJournalResponse.builder().id(5L).build());

        controller.reject(5L, request, PRINCIPAL);

        ArgumentCaptor<RejectManualJournalRequest> captor = ArgumentCaptor.forClass(RejectManualJournalRequest.class);
        verify(manualJournalService).reject(eq(5L), captor.capture());
        assertEquals(REAL_AUTHENTICATED_USER_ID, captor.getValue().getUserId());
    }

    @Test
    void postIgnoresAClientSuppliedUserIdAndUsesTheAuthenticatedPrincipal() {
        stubRealAuthenticatedUser();
        ManualJournalActionRequest request = new ManualJournalActionRequest();
        request.setUserId(FORGED_USER_ID);
        when(manualJournalService.post(eq(5L), any())).thenReturn(ManualJournalResponse.builder().id(5L).build());

        controller.post(5L, request, PRINCIPAL);

        ArgumentCaptor<ManualJournalActionRequest> captor = ArgumentCaptor.forClass(ManualJournalActionRequest.class);
        verify(manualJournalService).post(eq(5L), captor.capture());
        assertEquals(REAL_AUTHENTICATED_USER_ID, captor.getValue().getUserId());
    }

    @Test
    void approveReturnsUnauthorizedWhenThePrincipalDoesNotResolveToAnAccount() {
        UserDetails ghost = new User("ghost", "hashed", List.of());
        when(userAccountService.findByUsername("ghost")).thenReturn(Optional.empty());
        ManualJournalActionRequest request = new ManualJournalActionRequest();
        request.setUserId(FORGED_USER_ID);

        ResponseEntity<?> response = controller.approve(5L, request, ghost);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
}
