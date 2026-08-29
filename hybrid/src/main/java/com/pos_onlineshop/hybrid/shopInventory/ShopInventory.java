package com.pos_onlineshop.hybrid.shopInventory;

import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.shop.Shop;
import com.pos_onlineshop.hybrid.suppliers.Suppliers;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A FIFO cost layer: one immutable stock receipt (audit/record-keeping - {@code quantity} is
 * set once and never changed) paired with a mutable {@code remainingQuantity} that tracks how
 * much of this specific lot has not yet been consumed. This is what makes ShopInventory the
 * authoritative cost source for valuation and COGS - see InventoryValuationService, which is
 * the only place lots are actually consumed (decremented) or replenished (a new lot inserted).
 * InventoryTotal remains the authoritative live quantity; a (shop, product) pair's
 * {@code sum(remainingQuantity)} across its lots should equal InventoryTotal.totalstock, but
 * the two are reconciled by InventoryValuationService rather than assumed to always agree -
 * see its class comment for how a pre-existing (pre-FIFO) divergence is handled.
 *
 * Most lots originate from a real purchase (suppliers non-null, sourceReference null - the
 * implicit "ordinary purchase receipt" case, preserved for every historical row). A lot can
 * also be a system-restored layer - a sales return or a transfer-in - which has no supplier
 * of its own; sourceReference then names what created it (e.g. "SALES_RETURN-123",
 * "TRANSFER_IN-45") so every unit of remaining cost is traceable to why it exists.
 */
@Entity
@Table(name = "shop_inventories")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"shop", "product"})
@ToString(exclude = {"shop", "product"})
public class ShopInventory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    /** Null for a system-restored layer (sales return / transfer-in) - see sourceReference. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Suppliers suppliers;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "expiry_date")
    private LocalDateTime expiryDate;



    /** Immutable - the quantity this lot originally received. Never changes after insert. */
    @Column(nullable = false)
    @Builder.Default
    private Integer quantity = 0;

    /** How much of this lot's quantity has not yet been consumed by a sale, transfer-out, or
     * damage write-off - the field InventoryValuationService actually reads/writes for FIFO
     * costing. Deliberately nullable with NO default at the column level (not even 0): a lot
     * created going forward always has this set explicitly to {@code quantity} at insert, so
     * null unambiguously means "a pre-existing row from before FIFO cost layers existed, not
     * yet backfilled" - see InventoryValuationService.backfillLayersIfNeeded for the one-time,
     * disclosed assumption used to initialize these. A 0 can therefore always be trusted as
     * "genuinely fully consumed," never confused with "never touched." */
    @Column(name = "remaining_quantity")
    private Integer remainingQuantity;

    /** Null for an ordinary purchase receipt (the only kind of lot that existed before FIFO
     * cost layers). Set for a system-restored layer to name what created it, e.g.
     * "SALES_RETURN-123" or "TRANSFER_IN-45" - see the class comment. */
    @Column(name = "source_reference", length = 100)
    private String sourceReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;


    @Column(name = "reorder_level")
    private Integer reorderLevel;

    @Column(name = "min_stock")
    private Integer minStock;

    @Column(name = "max_stock")
    private Integer maxStock;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime addedAt = LocalDateTime.now();





}
