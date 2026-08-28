package com.pos_onlineshop.hybrid.customerInvoiceLine;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.pos_onlineshop.hybrid.customerInvoice.CustomerInvoice;
import com.pos_onlineshop.hybrid.products.Product;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "customer_invoice_lines")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"invoice", "product"})
@ToString(exclude = {"invoice", "product"})
public class CustomerInvoiceLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_invoice_id", nullable = false)
    @JsonIgnore
    private CustomerInvoice invoice;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    public BigDecimal getLineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
