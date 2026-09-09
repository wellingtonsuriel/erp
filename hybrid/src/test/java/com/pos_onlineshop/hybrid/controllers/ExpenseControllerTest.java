package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.CreateExpenseRequest;
import com.pos_onlineshop.hybrid.dtos.ExpenseResponse;
import com.pos_onlineshop.hybrid.dtos.RejectManualJournalRequest;
import com.pos_onlineshop.hybrid.security.AuthenticatedActorResolver;
import com.pos_onlineshop.hybrid.services.ExpenseCategoryService;
import com.pos_onlineshop.hybrid.services.ExpenseService;
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
 * Expense's maker-checker rule (the preparer can never also approve their own expense) only
 * holds if createdBy/approvedBy are the real authenticated identity - previously both came from
 * request-body fields (CreateExpenseRequest.createdByUserId, ManualJournalActionRequest/
 * RejectManualJournalRequest.userId) that any caller with GL_APPROVE could set to anything,
 * defeating the check by simply claiming mismatched identities on each side. Those userId fields
 * have since been removed from the DTOs entirely; these tests confirm the controller always
 * resolves the real principal (via AuthenticatedActorResolver) rather than trusting the body.
 */
@ExtendWith(MockitoExtension.class)
class ExpenseControllerTest {

    @Mock private ExpenseService expenseService;
    @Mock private ExpenseCategoryService expenseCategoryService;
    @Mock private AuthenticatedActorResolver actorResolver;

    private ExpenseController controller;

    private UserDetails principal(String username) {
        return new User(username, "hashed", true, true, true, true, List.of());
    }

    @Test
    void createUsesTheAuthenticatedPrincipalAsCreatorNeverTheRequestBody() {
        controller = new ExpenseController(expenseService, expenseCategoryService, actorResolver);
        UserDetails principal = principal("real-clerk");
        when(actorResolver.requireActingUserId(principal)).thenReturn(11L);
        when(expenseService.createExpense(any(), eq(11L)))
                .thenReturn(ExpenseResponse.builder().id(1L).status("DRAFT").build());

        CreateExpenseRequest request = new CreateExpenseRequest();
        request.setCreatedByUserId(9999L); // attacker-controlled value that must be ignored

        ResponseEntity<?> response = controller.create(request, principal);

        verify(expenseService).createExpense(request, 11L);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void approveUsesTheAuthenticatedPrincipalAsApproverNeverTheRequestBody() {
        controller = new ExpenseController(expenseService, expenseCategoryService, actorResolver);
        UserDetails principal = principal("real-manager");
        when(actorResolver.requireActingUserId(principal)).thenReturn(22L);
        when(expenseService.approveAndPay(5L, 22L))
                .thenReturn(ExpenseResponse.builder().id(5L).status("PAID").build());

        ResponseEntity<?> response = controller.approve(5L, principal);

        verify(expenseService).approveAndPay(5L, 22L);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void rejectUsesTheAuthenticatedPrincipalAsRejecterNeverTheRequestBody() {
        controller = new ExpenseController(expenseService, expenseCategoryService, actorResolver);
        UserDetails principal = principal("real-manager");
        when(actorResolver.requireActingUserId(principal)).thenReturn(33L);
        when(expenseService.reject(5L, 33L, "Missing receipt"))
                .thenReturn(ExpenseResponse.builder().id(5L).status("REJECTED").build());

        RejectManualJournalRequest request = new RejectManualJournalRequest();
        request.setReason("Missing receipt");

        ResponseEntity<?> response = controller.reject(5L, request, principal);

        verify(expenseService).reject(5L, 33L, "Missing receipt");
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void aCashierOnlyPrincipalCannotActOnExpensesAtAll() {
        controller = new ExpenseController(expenseService, expenseCategoryService, actorResolver);
        UserDetails principal = principal("cashier-admin");
        when(actorResolver.requireActingUserId(principal))
                .thenThrow(new IllegalStateException("Authenticated principal cashier-admin is not a UserAccount"));

        ResponseEntity<?> response = controller.approve(5L, principal);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}
