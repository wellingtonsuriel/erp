package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.approvalRequest.ApprovalRequest;
import com.pos_onlineshop.hybrid.approvalRequest.ApprovalRequestRepository;
import com.pos_onlineshop.hybrid.dtos.ApprovalRequestResponse;
import com.pos_onlineshop.hybrid.dtos.CreateApprovalRequestRequest;
import com.pos_onlineshop.hybrid.enums.ApprovalStatus;
import com.pos_onlineshop.hybrid.enums.NotificationType;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import com.pos_onlineshop.hybrid.userAccount.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkflowServiceTest {

    @Mock private ApprovalRequestRepository approvalRequestRepository;
    @Mock private UserAccountRepository userAccountRepository;
    @Mock private NotificationService notificationService;

    private WorkflowService service;
    private UserAccount requester;
    private UserAccount approver;

    @BeforeEach
    void setUp() {
        service = new WorkflowService(approvalRequestRepository, userAccountRepository, notificationService);
        requester = UserAccount.builder().id(1L).username("clerk1").password("x").email("clerk1@test.com").build();
        approver = UserAccount.builder().id(2L).username("manager1").password("x").email("manager1@test.com").build();
        lenient().when(userAccountRepository.findById(1L)).thenReturn(Optional.of(requester));
        lenient().when(userAccountRepository.findById(2L)).thenReturn(Optional.of(approver));
        lenient().when(approvalRequestRepository.save(any(ApprovalRequest.class))).thenAnswer(inv -> {
            ApprovalRequest r = inv.getArgument(0);
            if (r.getId() == null) r.setId(50L);
            return r;
        });
    }

    private CreateApprovalRequestRequest request() {
        CreateApprovalRequestRequest request = new CreateApprovalRequestRequest();
        request.setEntityType("SELLING_PRICE");
        request.setEntityId(7L);
        request.setAction("PRICE_CHANGE");
        request.setDetails("Increase from 10.00 to 12.00");
        request.setRequestedByUserId(1L);
        return request;
    }

    @Test
    void requestApprovalCreatesAPendingRequest() {
        // requestedByUserId is now a separate parameter (the authenticated caller's id), not
        // read from the request body - see WorkflowController's class comment.
        ApprovalRequestResponse response = service.requestApproval(request(), 1L);

        assertEquals("PENDING", response.getStatus());
        assertEquals("clerk1", response.getRequestedByUsername());
    }

    @Test
    void approveRejectsTheRequesterApprovingTheirOwnRequest() {
        ApprovalRequest approvalRequest = ApprovalRequest.builder().id(50L).entityType("SELLING_PRICE").entityId(7L)
                .action("PRICE_CHANGE").requestedBy(requester).status(ApprovalStatus.PENDING).build();
        when(approvalRequestRepository.findById(50L)).thenReturn(Optional.of(approvalRequest));

        assertThrows(IllegalStateException.class, () -> service.approve(50L, 1L, "looks fine"));
        verifyNoInteractions(notificationService);
    }

    @Test
    void approveMarksApprovedAndNotifiesTheRequester() {
        ApprovalRequest approvalRequest = ApprovalRequest.builder().id(50L).entityType("SELLING_PRICE").entityId(7L)
                .action("PRICE_CHANGE").requestedBy(requester).status(ApprovalStatus.PENDING).build();
        when(approvalRequestRepository.findById(50L)).thenReturn(Optional.of(approvalRequest));

        ApprovalRequestResponse response = service.approve(50L, 2L, "Looks good");

        assertEquals("APPROVED", response.getStatus());
        assertEquals("manager1", response.getDecidedByUsername());
        verify(notificationService).notify(eq(1L), eq(NotificationType.ACTION_COMPLETED), anyString(), anyString(),
                eq("APPROVAL_REQUEST"), eq(50L));
    }

    @Test
    void rejectMarksRejectedWithTheReason() {
        ApprovalRequest approvalRequest = ApprovalRequest.builder().id(50L).entityType("SELLING_PRICE").entityId(7L)
                .action("PRICE_CHANGE").requestedBy(requester).status(ApprovalStatus.PENDING).build();
        when(approvalRequestRepository.findById(50L)).thenReturn(Optional.of(approvalRequest));

        ApprovalRequestResponse response = service.reject(50L, 2L, "Price too high");

        assertEquals("REJECTED", response.getStatus());
        assertEquals("Price too high", response.getReason());
    }

    @Test
    void decideRejectsARequestThatIsAlreadyDecided() {
        ApprovalRequest approvalRequest = ApprovalRequest.builder().id(50L).entityType("SELLING_PRICE").entityId(7L)
                .action("PRICE_CHANGE").requestedBy(requester).status(ApprovalStatus.APPROVED).build();
        when(approvalRequestRepository.findById(50L)).thenReturn(Optional.of(approvalRequest));

        assertThrows(IllegalStateException.class, () -> service.approve(50L, 2L, "reason"));
    }

    @Test
    void requestApprovalUsesTheGivenRequesterIdNeverTheRequestBodysValue() {
        // The request body claims requestedByUserId=1 (clerk1), but the id actually passed in
        // (the resolved authenticated caller, per WorkflowController) is 2 (manager1) - the
        // saved request must be attributed to the real caller.
        CreateApprovalRequestRequest request = request();
        request.setRequestedByUserId(1L);

        ApprovalRequestResponse response = service.requestApproval(request, 2L);

        assertEquals("manager1", response.getRequestedByUsername());
        assertNotEquals("clerk1", response.getRequestedByUsername());
    }
}
