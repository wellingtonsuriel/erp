package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.dtos.NotificationResponse;
import com.pos_onlineshop.hybrid.enums.NotificationType;
import com.pos_onlineshop.hybrid.notification.Notification;
import com.pos_onlineshop.hybrid.notification.NotificationRepository;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import com.pos_onlineshop.hybrid.userAccount.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * In-app notifications. See WorkflowService for the one real trigger today: a requester is
 * notified when their ApprovalRequest is approved or rejected. Deliberately no email/SMS/push
 * channel - this is an in-app inbox (findForUser/unreadCount), matching what this codebase
 * can actually deliver today; adding an external channel is separate infrastructure this
 * service does not pretend to have.
 *
 * findForUser/findUnreadForUser/unreadCount/markRead's userId must always be the authenticated
 * caller's own id, resolved in NotificationController, never a request-supplied value - that
 * was a real IDOR letting any authenticated user read (or mark read) anyone else's notification
 * inbox by simply changing a query parameter or guessing/enumerating a notification id.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserAccountRepository userAccountRepository;

    public void notify(Long recipientUserId, NotificationType type, String title, String message,
                        String referenceType, Long referenceId) {
        UserAccount recipient = userAccountRepository.findById(recipientUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + recipientUserId));
        notificationRepository.save(Notification.builder()
                .recipient(recipient)
                .type(type)
                .title(title)
                .message(message)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .build());
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> findForUser(Long userId) {
        UserAccount recipient = resolveUser(userId);
        return notificationRepository.findByRecipientOrderByIdDesc(recipient).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> findUnreadForUser(Long userId) {
        UserAccount recipient = resolveUser(userId);
        return notificationRepository.findByRecipientAndReadFalseOrderByIdDesc(recipient).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notificationRepository.countByRecipientAndReadFalse(resolveUser(userId));
    }

    /**
     * @param actingUserId the authenticated caller's own id - a notification belonging to
     *                     anyone else is reported as not found rather than acted on, so one
     *                     account can never confirm the existence or contents of another
     *                     account's notification through this call.
     */
    public NotificationResponse markRead(Long notificationId, Long actingUserId) {
        Notification notification = notificationRepository.findById(notificationId)
                .filter(n -> n.getRecipient().getId().equals(actingUserId))
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + notificationId));
        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(LocalDateTime.now());
            notification = notificationRepository.save(notification);
        }
        return toResponse(notification);
    }

    private UserAccount resolveUser(Long userId) {
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

    private NotificationResponse toResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .type(notification.getType().name())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .referenceType(notification.getReferenceType())
                .referenceId(notification.getReferenceId())
                .read(notification.isRead())
                .createdAt(notification.getCreatedAt())
                .readAt(notification.getReadAt())
                .build();
    }
}
