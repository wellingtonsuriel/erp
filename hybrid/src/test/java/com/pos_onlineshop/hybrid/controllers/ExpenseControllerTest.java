package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.CreateExpenseRequest;
import com.pos_onlineshop.hybrid.dtos.ExpenseResponse;
import com.pos_onlineshop.hybrid.dtos.RejectManualJournalRequest;
import com.pos_onlineshop.hybrid.services.ExpenseCategoryService;
import com.pos_onlineshop.hybrid.services.ExpenseService;
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
 * Expense's maker-checker rule (the preparer can never also approve their own expense) only
 * holds if createdBy/approvedBy are the real authenticated identity - previously both came from
 * request-body fields (CreateExpenseRequest.createdByUserId, ManualJournalActionRequest/
 * RejectManualJournalRequest.userId) that any caller with GL_APPROVE could set to anything,
 * defeating the check by simply claiming mismatched identities on each side. These tests confirm
 * the controller now always resolves the real principal and ignores whatever the body claims.
 */
@ExtendWith(MockitoExtension.class)
class ExpenseControllerTest {

    @Mock private ExpenseService expenseService;
    @Mock private ExpenseCategoryService expenseCategoryService;
    @Mock private UserAccountService userAccountService;

    private ExpenseController controller;

    private UserDetails principal(String username) {
        return new User(username, "hashed", true, true, true, true, List.of());
    }

    @Test
    void createUsesTheAuthenticatedPrincipalAsCreatorNeverTheRequestBody() {
        controller = new ExpenseController(expenseService, expenseCategoryService, userAccountService);
        UserAccount realCreator = UserAccount.builder().id(11L).username("real-clerk").build();
        when(userAccountService.findByUsername("real-clerk")).thenReturn(Optional.of(realCreator));
        when(expenseService.createExpense(any(), eq(11L)))
                .thenReturn(ExpenseResponse.builder().id(1L).status("DRAFT").build());

        CreateExpenseRequest request = new CreateExpenseRequest();
        request.setCreatedByUserId(9999L); // attacker-controlled value that must be ignored

        ResponseEntity<?> response = controller.create(request, principal("real-clerk"));

        verify(expenseService).createExpense(request, 11L);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void approveUsesTheAuthenticatedPrincipalAsApproverNeverTheRequestBody() {
        controller = new ExpenseController(expenseService, expenseCategoryService, userAccountService);
        UserAccount realApprover = UserAccount.builder().id(22L).username("real-manager").build();
        when(userAccountService.findByUsername("real-manager")).thenReturn(Optional.of(realApprover));
        when(expenseService.approveAndPay(5L, 22L))
                .thenReturn(ExpenseResponse.builder().id(5L).status("PAID").build());

        ResponseEntity<?> response = controller.approve(5L, principal("real-manager"));

        verify(expenseService).approveAndPay(5L, 22L);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void rejectUsesTheAuthenticatedPrincipalAsRejecterNeverTheRequestBody() {
        controller = new ExpenseController(expenseService, expenseCategoryService, userAccountService);
        UserAccount realRejecter = UserAccount.builder().id(33L).username("real-manager").build();
        when(userAccountService.findByUsername("real-manager")).thenReturn(Optional.of(realRejecter));
        when(expenseService.reject(5L, 33L, "Missing receipt"))
                .thenReturn(ExpenseResponse.builder().id(5L).status("REJECTED").build());

        RejectManualJournalRequest request = new RejectManualJournalRequest();
        request.setUserId(9999L); // attacker-controlled value that must be ignored
        request.setReason("Missing receipt");

        ResponseEntity<?> response = controller.reject(5L, request, principal("real-manager"));

        verify(expenseService).reject(5L, 33L, "Missing receipt");
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void aCashierOnlyPrincipalCannotActOnExpensesAtAll() {
        controller = new ExpenseController(expenseService, expenseCategoryService, userAccountService);
        when(userAccountService.findByUsername("cashier-admin")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.approve(5L, principal("cashier-admin"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}
