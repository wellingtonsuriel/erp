package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.JournalEntryDetailResponse;
import com.pos_onlineshop.hybrid.dtos.ReverseJournalEntryRequest;
import com.pos_onlineshop.hybrid.services.JournalEntryService;
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
 * reverse previously read postedBy from ReverseJournalEntryRequest.postedBy (defaulting to
 * "system" when absent) - any GL_REVERSE-authorized caller could claim any string as who
 * reversed a posted journal entry. This test confirms the controller now always uses the
 * authenticated principal's username.
 */
@ExtendWith(MockitoExtension.class)
class JournalEntryControllerTest {

    @Mock private JournalEntryService journalEntryService;

    private JournalEntryController controller;

    @Test
    void reverseUsesTheAuthenticatedPrincipalsUsernameNeverTheRequestBody() {
        controller = new JournalEntryController(journalEntryService);
        when(journalEntryService.reverse(org.mockito.ArgumentMatchers.eq(5L), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("real-admin")))
                .thenReturn(JournalEntryDetailResponse.builder().id(5L).build());

        ReverseJournalEntryRequest request = new ReverseJournalEntryRequest();
        request.setReason("Correction");
        request.setPostedBy("attacker-controlled-value"); // must be ignored

        UserDetails principal = new User("real-admin", "hashed", true, true, true, true, List.of());
        controller.reverse(5L, request, principal);

        verify(journalEntryService).reverse(5L, request, "real-admin");
    }
}
