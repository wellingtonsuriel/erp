package com.pos_onlineshop.hybrid.fxRevaluation;

import com.pos_onlineshop.hybrid.enums.FxInvoiceType;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Audit record of one period-end unrealized FX revaluation of a single open foreign-currency
 * invoice - see FxRevaluationService's class comment for the full mechanism. Records exactly
 * the rate movement this revaluation recognized (priorRate -> newRate), not just the
 * resulting balance, specifically so the next revaluation's "how much has this balance moved
 * since it was last restated" question has a real answer instead of re-deriving it from the
 * invoice's current (already-updated) exchangeRate field.
 */
@Entity
@Table(name = "fx_revaluation_entries")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"postedJournalEntry"})
@ToString(exclude = {"postedJournalEntry"})
public class FxRevaluationEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "invoice_type", nullable = false)
    private FxInvoiceType invoiceType;

    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    @Column(name = "invoice_number", nullable = false, length = 60)
    private String invoiceNumber;

    @Column(name = "revaluation_date", nullable = false)
    private LocalDate revaluationDate;

    /** The rate this balance was carried at before this revaluation - either the invoice's
     * original booking rate (first revaluation) or the newRate of the most recent prior
     * revaluation of this same invoice. */
    @Column(name = "prior_rate", nullable = false, precision = 19, scale = 6)
    private BigDecimal priorRate;

    /** The period-end closing rate this balance is now carried at - becomes the new
     * CustomerInvoice/SupplierInvoice.exchangeRate, and this revaluation's own priorRate next
     * time. */
    @Column(name = "new_rate", nullable = false, precision = 19, scale = 6)
    private BigDecimal newRate;

    /** Outstanding balance in the invoice's transaction currency at the time of revaluation. */
    @Column(name = "outstanding_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal outstandingAmount;

    /** Base-currency amount of this revaluation - positive is a gain, negative a loss. */
    @Column(name = "unrealized_gain_loss", nullable = false, precision = 19, scale = 4)
    private BigDecimal unrealizedGainLoss;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posted_journal_entry_id", nullable = false)
    private JournalEntry postedJournalEntry;
}
