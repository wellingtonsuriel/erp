package com.pos_onlineshop.hybrid.supplierDebitNote;

import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.enums.DebitNoteStatus;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.suppliers.Suppliers;
import com.pos_onlineshop.hybrid.supplierInvoice.SupplierInvoice;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Reduces a SupplierInvoice's outstanding balance without cash changing hands - goods
 * returned to the supplier, a pricing correction, or a billing dispute resolved in our
 * favor. DRAFT -> POSTED (applies to the invoice via SupplierInvoice.applyPayment) -> a
 * terminal state; VOID only from DRAFT - see CustomerCreditNote's identical reasoning for
 * why posting isn't reversible here yet.
 *
 * Unlike CustomerCreditNote, the GL credit side cannot be a fixed account: a SupplierInvoice
 * is either PO-linked (goods bought on credit, AP booked at goods receipt against 1200
 * Inventory) or standalone (a service/expense invoice, AP booked against 5300 Operating
 * Expenses - see SupplierInvoiceService). A debit note against a PO-linked invoice credits
 * 1200 (undoing part of the stock increase, mirroring STOCK_RECEIPT's own DEBIT 1200/CREDIT
 * 2100 in reverse); against a standalone invoice it credits 5300. That per-invocation choice
 * is why SupplierDebitNoteService posts via GLPostingService.postManual() with an explicitly
 * chosen account, rather than a static PostingRule.
 */
@Entity
@Table(name = "supplier_debit_notes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"supplier", "invoice", "currency", "postedJournalEntry"})
@ToString(exclude = {"supplier", "invoice", "currency", "postedJournalEntry"})
public class SupplierDebitNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "debit_note_number", nullable = false, unique = true, length = 60)
    private String debitNoteNumber;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Suppliers supplier;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "invoice_id", nullable = false)
    private SupplierInvoice invoice;

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
    private DebitNoteStatus status = DebitNoteStatus.DRAFT;

    @Column(name = "voided_reason", length = 500)
    private String voidedReason;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posted_journal_entry_id")
    private JournalEntry postedJournalEntry;

    public boolean canBePosted() {
        return status == DebitNoteStatus.DRAFT;
    }

    public boolean canBeVoided() {
        return status == DebitNoteStatus.DRAFT;
    }

    public void post(JournalEntry entry) {
        if (!canBePosted()) {
            throw new IllegalStateException("Debit note " + debitNoteNumber + " cannot be posted from status " + status);
        }
        this.status = DebitNoteStatus.POSTED;
        this.postedJournalEntry = entry;
    }

    public void voidDebitNote(String reason) {
        if (!canBeVoided()) {
            throw new IllegalStateException("Debit note " + debitNoteNumber + " cannot be voided from status " + status);
        }
        this.status = DebitNoteStatus.VOID;
        this.voidedReason = reason;
    }
}
