package com.pos_onlineshop.hybrid.inventoryMovement;

import com.pos_onlineshop.hybrid.enums.InventoryMovementType;
import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.shop.Shop;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * An immutable, append-only audit record of one quantity change to InventoryTotal - the
 * "why did this number change" ledger that InventoryTotal itself (a running counter) cannot
 * answer on its own. Written by ShopInventoryService alongside every InventoryTotal mutation,
 * since that service is already the sole gateway to InventoryTotal (see its class comment) -
 * this makes every quantity change auditable without every caller (POS, online orders,
 * transfers, damage) having to remember to log one separately.
 *
 * Deliberately separate from both ShopInventory (which tracks cost layers, not a movement
 * log) and JournalEntry (which is the monetary record, not a quantity one) - see the master
 * accounting-architecture note on why quantity and money must stay distinguishable.
 *
 * quantity is always positive; movementType says whether it was an increase or decrease -
 * this mirrors how a bank statement records amount + debit/credit rather than a signed value,
 * and keeps "how many units moved" and "which direction" independently readable.
 */
@Entity
@Table(name = "inventory_movements")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"shop", "product"})
@ToString(exclude = {"shop", "product"})
public class InventoryMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false)
    private InventoryMovementType movementType;

    /** Always positive - see the class comment on why direction is carried by movementType
     * rather than sign. */
    @Column(nullable = false)
    private Integer quantity;

    /** The FIFO-weighted unit cost this movement was valued at, when one applies (null for a
     * RESERVATION/RESERVATION_RELEASE, which never has a cost - reserving is not a sale). */
    @Column(name = "unit_cost", precision = 19, scale = 4)
    private BigDecimal unitCost;

    /** Free-text pointer to the originating document, e.g. "ORDER-123", "TRANSFER-45",
     * "DAMAGE-7" - not a foreign key, since the source varies by movementType. */
    @Column(length = 100)
    private String reference;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
