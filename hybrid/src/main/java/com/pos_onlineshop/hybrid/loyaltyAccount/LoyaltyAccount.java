package com.pos_onlineshop.hybrid.loyaltyAccount;

import com.pos_onlineshop.hybrid.customers.Customers;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One customer's monetary loyalty/deposit balance - distinct from the pre-existing
 * Customers.loyaltyPoints (a plain gamification counter with no accounting behind it at
 * all). This is the real GL-integrated ledger: availableBalance is always the sum of every
 * LoyaltyTransaction for this account and is what 2300 Customer Deposits & Loyalty
 * Liability should reconcile to. totalEarned/Redeemed/Expired/Reversed are running
 * lifetime totals for reporting only - they never decrease.
 */
@Entity
@Table(name = "loyalty_accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"customer"})
@ToString(exclude = {"customer"})
public class LoyaltyAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false, unique = true)
    private Customers customer;

    @Column(name = "available_balance", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal availableBalance = BigDecimal.ZERO;

    @Column(name = "total_earned", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal totalEarned = BigDecimal.ZERO;

    @Column(name = "total_redeemed", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal totalRedeemed = BigDecimal.ZERO;

    @Column(name = "total_expired", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal totalExpired = BigDecimal.ZERO;

    @Column(name = "total_reversed", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal totalReversed = BigDecimal.ZERO;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
