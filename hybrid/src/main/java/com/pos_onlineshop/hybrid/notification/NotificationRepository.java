package com.pos_onlineshop.hybrid.notification;

import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByRecipientOrderByIdDesc(UserAccount recipient);

    List<Notification> findByRecipientAndReadFalseOrderByIdDesc(UserAccount recipient);

    long countByRecipientAndReadFalse(UserAccount recipient);
}
