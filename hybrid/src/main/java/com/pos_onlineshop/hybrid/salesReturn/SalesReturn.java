package com.pos_onlineshop.hybrid.salesReturn;

import com.pos_onlineshop.hybrid.customers.Customers;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.orders.Order;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A full or partial return against a specific Order - see SalesReturnService for why this
 * reverses the original sale's economics (revenue, tax, COGS, inventory) rather than posting
 * an arbitrary new transaction. No DRAFT state: SalesReturnService.createReturn() computes
 * every line and posts the reversal atomically, the same "one action, one transaction"
 * pattern used throughout this codebase. Never edited once created - a mistake is corrected
 * by a fresh return against the same order's remaining returnable quantity, not by rewriting
 * this record.
 */
@Entity
@Table(name = "sales_returns")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"order", "customer", "createdBy", "journalEntry", "lines"})
@ToString(exclude = {"order", "customer", "createdBy", "journalEntry", "lines"})
public class SalesReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "return_number", nullable = false, unique = true, length = 60)
    private String returnNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customers customer;

    @Column(nullable = false)
    private String reason;

    @Column(name = "return_date", nullable = false)
    private LocalDate returnDate;

    @Column(name = "total_refund_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalRefundAmount;

    @Column(name = "total_tax_reversed", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalTaxReversed;

    /** Null when the original sale's per-line cost wasn't known (see OrderLine.unitCost) -
     * in that case inventory quantity is still restored, but no COGS/1200 GL movement is
     * posted, the same "never manufacture a cost" rule the original sale follows. */
    @Column(name = "total_cost_reversed", precision = 19, scale = 4)
    private BigDecimal totalCostReversed;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false)
    private UserAccount createdBy;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id")
    private JournalEntry journalEntry;

    @OneToMany(mappedBy = "salesReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SalesReturnLine> lines = new ArrayList<>();
}
