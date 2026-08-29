package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.dtos.NotificationResponse;
import com.pos_onlineshop.hybrid.enums.NotificationType;
import com.pos_onlineshop.hybrid.notification.Notification;
import com.pos_onlineshop.hybrid.notification.NotificationRepository;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import com.pos_onlineshop.hybrid.userAccount.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private UserAccountRepository userAccountRepository;

    private NotificationService service;
    private UserAccount recipient;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository, userAccountRepository);
        recipient = UserAccount.builder().id(1L).username("clerk1").password("x").email("clerk1@test.com").build();
        lenient().when(userAccountRepository.findById(1L)).thenReturn(Optional.of(recipient));
    }

    @Test
    void notifyRejectsAnUnknownRecipient() {
        when(userAccountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.notify(99L, NotificationType.INFO, "Title", "Message", null, null));
    }

    @Test
    void notifySavesANotificationForTheRecipient() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        service.notify(1L, NotificationType.APPROVAL_REQUIRED, "Approval needed", "Please review", "APPROVAL_REQUEST", 5L);

        verify(notificationRepository).save(argThat(n ->
                n.getRecipient() == recipient && n.getType() == NotificationType.APPROVAL_REQUIRED
                        && "Approval needed".equals(n.getTitle()) && "APPROVAL_REQUEST".equals(n.getReferenceType())));
    }

    @Test
    void markReadSetsReadAndReadAt() {
        Notification notification = Notification.builder().id(10L).recipient(recipient)
                .type(NotificationType.INFO).title("Title").message("Message").read(false).build();
        when(notificationRepository.findById(10L)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationResponse response = service.markRead(10L);

        assertTrue(response.isRead());
        assertNotNull(response.getReadAt());
    }

    @Test
    void markReadIsANoOpForAnAlreadyReadNotification() {
        Notification notification = Notification.builder().id(10L).recipient(recipient)
                .type(NotificationType.INFO).title("Title").message("Message").read(true).build();
        when(notificationRepository.findById(10L)).thenReturn(Optional.of(notification));

        service.markRead(10L);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void findUnreadForUserDelegatesToTheUnreadQuery() {
        when(notificationRepository.findByRecipientAndReadFalseOrderByIdDesc(recipient)).thenReturn(List.of(
                Notification.builder().id(1L).recipient(recipient).type(NotificationType.INFO)
                        .title("T").message("M").read(false).build()));

        List<NotificationResponse> results = service.findUnreadForUser(1L);

        assertEquals(1, results.size());
    }
}
