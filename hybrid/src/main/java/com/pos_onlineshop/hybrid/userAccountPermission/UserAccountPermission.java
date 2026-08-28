package com.pos_onlineshop.hybrid.userAccountPermission;

import com.pos_onlineshop.hybrid.enums.AccountingPermission;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** One AccountingPermission grant on one UserAccount. Mirrors CashierPermission's shape. */
@Entity
@Table(name = "user_account_permissions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_account_id", "permission"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"userAccount", "grantedBy"})
@ToString(exclude = {"userAccount", "grantedBy"})
public class UserAccountPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_account_id", nullable = false)
    private UserAccount userAccount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountingPermission permission;

    @Column(name = "granted_at", nullable = false)
    @Builder.Default
    private LocalDateTime grantedAt = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by_id")
    private UserAccount grantedBy;
}
