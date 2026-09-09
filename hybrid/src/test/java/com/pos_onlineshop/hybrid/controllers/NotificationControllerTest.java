package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.NotificationResponse;
import com.pos_onlineshop.hybrid.security.AuthenticatedActorResolver;
import com.pos_onlineshop.hybrid.services.NotificationService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * list/unread/unreadCount previously took the target userId as a plain, client-suppliable query
 * parameter - any authenticated user could read any other user's notification inbox by changing
 * it. markRead had no ownership check at all. These tests confirm every endpoint now always
 * uses the authenticated caller's own identity.
 */
@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock private NotificationService notificationService;
    @Mock private AuthenticatedActorResolver actorResolver;

    private NotificationController controller;

    private UserDetails principal(String username) {
        return new User(username, "hashed", true, true, true, true, List.of());
    }

    @Test
    void listUsesTheAuthenticatedPrincipalsOwnId() {
        controller = new NotificationController(notificationService, actorResolver);
        UserDetails principal = principal("real-user");
        when(actorResolver.requireActingUserId(principal)).thenReturn(11L);
        when(notificationService.findForUser(11L)).thenReturn(List.of());

        ResponseEntity<?> response = controller.list(principal);

        verify(notificationService).findForUser(11L);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void markReadUsesTheAuthenticatedPrincipalsOwnId() {
        controller = new NotificationController(notificationService, actorResolver);
        UserDetails principal = principal("real-user");
        when(actorResolver.requireActingUserId(principal)).thenReturn(11L);
        when(notificationService.markRead(5L, 11L)).thenReturn(NotificationResponse.builder().id(5L).build());

        ResponseEntity<?> response = controller.markRead(5L, principal);

        verify(notificationService).markRead(5L, 11L);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void aCashierOnlyPrincipalCannotAccessNotificationsAtAll() {
        controller = new NotificationController(notificationService, actorResolver);
        UserDetails principal = principal("cashier-admin");
        when(actorResolver.requireActingUserId(principal))
                .thenThrow(new IllegalStateException("Authenticated principal cashier-admin is not a UserAccount"));

        ResponseEntity<?> response = controller.list(principal);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}
