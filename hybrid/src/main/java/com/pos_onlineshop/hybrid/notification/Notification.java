package com.pos_onlineshop.hybrid.notification;

import com.pos_onlineshop.hybrid.enums.NotificationType;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One in-app notification for one recipient - referenceType/referenceId optionally link
 * back to what caused it (e.g. an ApprovalRequest), never required. Read state is tracked
 * per-notification (read/readAt), not globally, since the same event can notify several
 * users independently. See NotificationService for who actually triggers these today
 * (WorkflowService, on an approval decision) - genuinely wired, not a placeholder no caller
 * ever reaches.
 */
@Entity
@Table(name = "notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"recipient"})
@ToString(exclude = {"recipient"})
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private UserAccount recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "reference_type", length = 60)
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(nullable = false)
    @Builder.Default
    private boolean read = false;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "read_at")
    private LocalDateTime readAt;
}
