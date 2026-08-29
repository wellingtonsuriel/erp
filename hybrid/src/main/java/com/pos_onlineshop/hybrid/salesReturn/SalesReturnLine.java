package com.pos_onlineshop.hybrid.salesReturn;

import com.pos_onlineshop.hybrid.orderLines.OrderLine;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * One returned quantity against a specific original OrderLine - unitPrice/unitCost are
 * copied from that OrderLine at return time (the price/cost the customer was actually
 * charged/the business actually paid), never re-priced at today's rates.
 */
@Entity
@Table(name = "sales_return_lines")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"salesReturn", "orderLine"})
@ToString(exclude = {"salesReturn", "orderLine"})
public class SalesReturnLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_return_id", nullable = false)
    private SalesReturn salesReturn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_line_id", nullable = false)
    private OrderLine orderLine;

    @Column(name = "quantity_returned", nullable = false)
    private Integer quantityReturned;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount;

    @Column(name = "unit_cost", precision = 19, scale = 4)
    private BigDecimal unitCost;
}
