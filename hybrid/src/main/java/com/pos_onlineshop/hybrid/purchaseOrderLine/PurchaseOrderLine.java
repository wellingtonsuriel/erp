package com.pos_onlineshop.hybrid.purchaseOrderLine;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.purchaseOrder.PurchaseOrder;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "purchase_order_lines")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"purchaseOrder", "product"})
@ToString(exclude = {"purchaseOrder", "product"})
public class PurchaseOrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    @JsonIgnore
    private PurchaseOrder purchaseOrder;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "quantity_ordered", nullable = false)
    private Integer quantityOrdered;

    @Column(name = "quantity_received", nullable = false)
    @Builder.Default
    private Integer quantityReceived = 0;

    /** Ex-tax unit cost, in the purchase order's currency. */
    @Column(name = "unit_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitCost;

    @Column(length = 500)
    private String notes;

    public Integer getOutstandingQuantity() {
        return quantityOrdered - quantityReceived;
    }

    public boolean isFullyReceived() {
        return quantityReceived >= quantityOrdered;
    }

    public BigDecimal getLineTotal() {
        return unitCost.multiply(BigDecimal.valueOf(quantityOrdered));
    }

    public void applyReceipt(int receivedQuantity) {
        int outstanding = getOutstandingQuantity();
        if (receivedQuantity <= 0) {
            throw new IllegalArgumentException("Received quantity must be positive");
        }
        if (receivedQuantity > outstanding) {
            throw new IllegalArgumentException(
                    "Cannot receive " + receivedQuantity + " units of " + product.getName()
                            + " - only " + outstanding + " outstanding on this purchase order line");
        }
        this.quantityReceived += receivedQuantity;
    }
}
