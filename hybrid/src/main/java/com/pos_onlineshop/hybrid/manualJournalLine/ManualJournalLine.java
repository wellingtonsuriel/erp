package com.pos_onlineshop.hybrid.manualJournalLine;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.manualJournal.ManualJournal;
import com.pos_onlineshop.hybrid.shop.Shop;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/** One line of a not-yet-posted ManualJournal. Mirrors JournalLine's shape (exactly one of
 * debit/credit set) so building the real JournalLine at post time is a direct copy. */
@Entity
@Table(name = "manual_journal_lines")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"manualJournal", "account", "currency", "costCenterShop"})
@ToString(exclude = {"manualJournal", "account", "currency", "costCenterShop"})
public class ManualJournalLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manual_journal_id", nullable = false)
    private ManualJournal manualJournal;

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

    @Column(name = "exchange_rate", nullable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal exchangeRate = BigDecimal.ONE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cost_center_shop_id")
    private Shop costCenterShop;

    @Column(length = 300)
    private String memo;
}
