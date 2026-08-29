package com.pos_onlineshop.hybrid.approvalRequest;

import com.pos_onlineshop.hybrid.enums.ApprovalStatus;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A generic, standalone maker-checker primitive - entityType/entityId/action describe what
 * is being approved (e.g. "SELLING_PRICE"/7/"PRICE_CHANGE"), independent of any specific
 * module's own workflow. The requester may never also be the approver, the same rule
 * ManualJournal enforces for its own bespoke flow - see WorkflowService.approve().
 *
 * Deliberately NOT retrofitted onto ManualJournal or Expense's existing maker-checker logic:
 * both already have real, working, tested approval flows of their own, and rerouting them
 * through a new generic engine would risk regressing working functionality for no
 * user-visible benefit. This is for a NEW flow that wants approval gating without building
 * its own status machine from scratch - see WorkflowService's class comment.
 */
@Entity
@Table(name = "approval_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"requestedBy", "decidedBy"})
@ToString(exclude = {"requestedBy", "decidedBy"})
public class ApprovalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false, length = 60)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(nullable = false, length = 60)
    private String action;

    private String details;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_id", nullable = false)
    private UserAccount requestedBy;

    @Column(name = "requested_at", nullable = false)
    @Builder.Default
    private LocalDateTime requestedAt = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by_id")
    private UserAccount decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    private String reason;
}
