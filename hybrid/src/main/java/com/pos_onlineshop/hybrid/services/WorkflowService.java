package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.approvalRequest.ApprovalRequest;
import com.pos_onlineshop.hybrid.approvalRequest.ApprovalRequestRepository;
import com.pos_onlineshop.hybrid.dtos.ApprovalRequestResponse;
import com.pos_onlineshop.hybrid.dtos.CreateApprovalRequestRequest;
import com.pos_onlineshop.hybrid.enums.ApprovalStatus;
import com.pos_onlineshop.hybrid.enums.NotificationType;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import com.pos_onlineshop.hybrid.userAccount.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A generic, reusable maker-checker primitive for a NEW flow that wants approval gating
 * without building its own status machine - see ApprovalRequest's class comment for why
 * this deliberately does not replace ManualJournal's or Expense's existing bespoke
 * maker-checker logic. The requester may never also be the decider, the same rule those
 * modules already enforce. On approve()/reject(), the requester is notified via
 * NotificationService - the one concrete integration point this stage wires end to end.
 */
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final ApprovalRequestRepository approvalRequestRepository;
    private final UserAccountRepository userAccountRepository;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<ApprovalRequestResponse> findAll() {
        return approvalRequestRepository.findAllByOrderByIdDesc().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequestResponse> findPending() {
        return approvalRequestRepository.findByStatusOrderByIdDesc(ApprovalStatus.PENDING).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public ApprovalRequestResponse requestApproval(CreateApprovalRequestRequest request) {
        UserAccount requestedBy = resolveUser(request.getRequestedByUserId());
        ApprovalRequest approvalRequest = ApprovalRequest.builder()
                .entityType(request.getEntityType())
                .entityId(request.getEntityId())
                .action(request.getAction())
                .details(request.getDetails())
                .requestedBy(requestedBy)
                .build();
        return toResponse(approvalRequestRepository.save(approvalRequest));
    }

    @Transactional
    public ApprovalRequestResponse approve(Long id, Long userId, String reason) {
        return decide(id, userId, reason, ApprovalStatus.APPROVED,
                "Approval request #%d (%s) was approved");
    }

    @Transactional
    public ApprovalRequestResponse reject(Long id, Long userId, String reason) {
        return decide(id, userId, reason, ApprovalStatus.REJECTED,
                "Approval request #%d (%s) was rejected");
    }

    private ApprovalRequestResponse decide(Long id, Long userId, String reason, ApprovalStatus outcome, String notificationMessage) {
        ApprovalRequest approvalRequest = approvalRequestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Approval request not found: " + id));
        if (approvalRequest.getStatus() != ApprovalStatus.PENDING) {
            throw new IllegalStateException("Approval request " + id + " is already " + approvalRequest.getStatus());
        }
        UserAccount decidedBy = resolveUser(userId);
        if (approvalRequest.getRequestedBy().getId().equals(decidedBy.getId())) {
            throw new IllegalStateException("The requester cannot also decide their own approval request");
        }

        approvalRequest.setStatus(outcome);
        approvalRequest.setDecidedBy(decidedBy);
        approvalRequest.setDecidedAt(LocalDateTime.now());
        approvalRequest.setReason(reason);
        ApprovalRequest saved = approvalRequestRepository.save(approvalRequest);

        notificationService.notify(saved.getRequestedBy().getId(), NotificationType.ACTION_COMPLETED,
                "Approval " + outcome.name().toLowerCase(),
                String.format(notificationMessage, saved.getId(), saved.getAction()),
                "APPROVAL_REQUEST", saved.getId());

        return toResponse(saved);
    }

    private UserAccount resolveUser(Long userId) {
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

    private ApprovalRequestResponse toResponse(ApprovalRequest approvalRequest) {
        return ApprovalRequestResponse.builder()
                .id(approvalRequest.getId())
                .entityType(approvalRequest.getEntityType())
                .entityId(approvalRequest.getEntityId())
                .action(approvalRequest.getAction())
                .details(approvalRequest.getDetails())
                .requestedById(approvalRequest.getRequestedBy().getId())
                .requestedByUsername(approvalRequest.getRequestedBy().getUsername())
                .requestedAt(approvalRequest.getRequestedAt())
                .status(approvalRequest.getStatus().name())
                .decidedById(approvalRequest.getDecidedBy() != null ? approvalRequest.getDecidedBy().getId() : null)
                .decidedByUsername(approvalRequest.getDecidedBy() != null ? approvalRequest.getDecidedBy().getUsername() : null)
                .decidedAt(approvalRequest.getDecidedAt())
                .reason(approvalRequest.getReason())
                .build();
    }
}
