package com.pos_onlineshop.hybrid.supplierInvoiceLine;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.supplierInvoice.SupplierInvoice;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "supplier_invoice_lines")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"invoice", "product"})
@ToString(exclude = {"invoice", "product"})
public class SupplierInvoiceLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_invoice_id", nullable = false)
    @JsonIgnore
    private SupplierInvoice invoice;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitCost;

    public BigDecimal getLineTotal() {
        return unitCost.multiply(BigDecimal.valueOf(quantity));
    }
}
