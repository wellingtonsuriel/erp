package com.pos_onlineshop.hybrid.journalLine;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.shop.Shop;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * One account touched by a JournalEntry. Exactly one of debitAmount/creditAmount is
 * non-zero; the other is zero (never both) - enforced by JournalValidator before persist.
 */
@Entity
@Table(name = "gl_journal_lines")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"journalEntry", "account", "currency", "costCenterShop"})
@ToString(exclude = {"journalEntry", "account", "currency", "costCenterShop"})
public class JournalLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id", nullable = false)
    private JournalEntry journalEntry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "debit_amount", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal debitAmount = BigDecimal.ZERO;

    @Column(name = "credit_amount", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal creditAmount = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    @Column(name = "base_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal baseAmount;

    @Column(name = "exchange_rate", nullable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal exchangeRate = BigDecimal.ONE;

    /** Shop dimension. The account itself is never exploded per shop. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cost_center_shop_id")
    private Shop costCenterShop;

    @Column(length = 300)
    private String memo;
}
