package com.pos_onlineshop.hybrid.customerCreditNote;

import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.customerInvoice.CustomerInvoice;
import com.pos_onlineshop.hybrid.customers.Customers;
import com.pos_onlineshop.hybrid.enums.CreditNoteStatus;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Reduces a CustomerInvoice's outstanding balance without cash changing hands - a return,
 * pricing correction, or goodwill write-down. DRAFT -> POSTED (applies to the invoice via
 * CustomerInvoice.applyPayment - the same guard a real receipt uses, since a credit note is
 * economically identical to the invoice: it reduces the same outstanding balance) -> a
 * terminal state; VOID is only reachable from DRAFT (posting is not reversible here - doing
 * so would need to un-apply the invoice balance and reverse the GL entry, which needs its
 * own dedicated flow, not yet built). Always standalone, mirroring CustomerInvoice itself -
 * see FinancialEventType.CUSTOMER_CREDIT_NOTE.
 */
@Entity
@Table(name = "customer_credit_notes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"customer", "invoice", "currency", "postedJournalEntry"})
@ToString(exclude = {"customer", "invoice", "currency", "postedJournalEntry"})
public class CustomerCreditNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "credit_note_number", nullable = false, unique = true, length = 60)
    private String creditNoteNumber;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customers customer;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "invoice_id", nullable = false)
    private CustomerInvoice invoice;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CreditNoteStatus status = CreditNoteStatus.DRAFT;

    @Column(name = "voided_reason", length = 500)
    private String voidedReason;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posted_journal_entry_id")
    private JournalEntry postedJournalEntry;

    public boolean canBePosted() {
        return status == CreditNoteStatus.DRAFT;
    }

    public boolean canBeVoided() {
        return status == CreditNoteStatus.DRAFT;
    }

    public void post(JournalEntry entry) {
        if (!canBePosted()) {
            throw new IllegalStateException("Credit note " + creditNoteNumber + " cannot be posted from status " + status);
        }
        this.status = CreditNoteStatus.POSTED;
        this.postedJournalEntry = entry;
    }

    public void voidCreditNote(String reason) {
        if (!canBeVoided()) {
            throw new IllegalStateException("Credit note " + creditNoteNumber + " cannot be voided from status " + status);
        }
        this.status = CreditNoteStatus.VOID;
        this.voidedReason = reason;
    }
}
