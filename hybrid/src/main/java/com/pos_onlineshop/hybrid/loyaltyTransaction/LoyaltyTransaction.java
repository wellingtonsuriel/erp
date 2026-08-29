package com.pos_onlineshop.hybrid.loyaltyTransaction;

import com.pos_onlineshop.hybrid.enums.LoyaltyTransactionType;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.loyaltyAccount.LoyaltyAccount;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One movement of a LoyaltyAccount's balance - amount is always positive, direction is
 * implied by transactionType (see LoyaltyTransactionType). balanceAfter is a snapshot of
 * LoyaltyAccount.availableBalance immediately after this transaction, for an auditable
 * history independent of the mutable account row. referenceType/referenceId optionally
 * link back to what caused the movement (e.g. an Order for an EARNED entry) - never
 * required, since not every movement has one (a manual EXPIRED sweep, for instance).
 */
@Entity
@Table(name = "loyalty_transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"loyaltyAccount", "createdBy", "journalEntry"})
@ToString(exclude = {"loyaltyAccount", "createdBy", "journalEntry"})
public class LoyaltyTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loyalty_account_id", nullable = false)
    private LoyaltyAccount loyaltyAccount;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private LoyaltyTransactionType transactionType;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceAfter;

    @Column(name = "reference_type", length = 40)
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    private String description;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false)
    private UserAccount createdBy;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id")
    private JournalEntry journalEntry;
}
