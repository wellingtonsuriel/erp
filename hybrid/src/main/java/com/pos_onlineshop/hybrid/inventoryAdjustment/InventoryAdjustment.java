package com.pos_onlineshop.hybrid.inventoryAdjustment;

import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.shop.Shop;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A manual, ad-hoc correction to a shop's live stock quantity (e.g. a physical stock count
 * finding more or less on the shelf than InventoryTotal records) - distinct from a sale,
 * transfer, or damage report, which each have their own dedicated flow. Unlike those, an
 * adjustment has no natural "other side" of a business transaction to derive cost from, so
 * an increase requires an explicit unit cost from the caller (never guessed) while a decrease
 * is costed from the real FIFO layers it consumes - see InventoryAdjustmentService.
 *
 * Always posts through the GL to the 5110 Inventory Adjustment Gain/Loss account rather than
 * silently changing a quantity with no accounting effect - see the "never confuse units with
 * money" rule. A decrease that FIFO layers cannot fully cost still reduces InventoryTotal (the
 * shelf really did lose that many units) but posts no GL line for the uncosted portion, the
 * same "never guess a value" convention InventoryValuationService itself documents.
 */
@Entity
@Table(name = "inventory_adjustments", uniqueConstraints = @UniqueConstraint(columnNames = "reference"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"shop", "product", "createdBy", "postedJournalEntry"})
@ToString(exclude = {"shop", "product", "createdBy", "postedJournalEntry"})
public class InventoryAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Caller-supplied, unique - doubles as the GL idempotency key so a retried request
     * replays the original adjustment instead of double-counting it. */
    @Column(nullable = false, unique = true, length = 100)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Positive = surplus found (stock increases), negative = shortage found (stock
     * decreases). Never zero - validated at creation. */
    @Column(name = "quantity_delta", nullable = false)
    private int quantityDelta;

    @Column(nullable = false, length = 500)
    private String reason;

    /** Required only for a positive quantityDelta - the cost basis for the new FIFO layer.
     * Null for a decrease, which is costed from real layers instead. */
    @Column(name = "unit_cost", precision = 19, scale = 4)
    private BigDecimal unitCost;

    /** The actual monetary amount posted to the GL - may be less than
     * abs(quantityDelta) * unitCost for a decrease that FIFO layers could not fully cost,
     * or zero if nothing could be costed at all (in which case postedJournalEntry is null). */
    @Column(name = "total_value", precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal totalValue = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false)
    private UserAccount createdBy;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posted_journal_entry_id")
    private JournalEntry postedJournalEntry;
}
