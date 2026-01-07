package com.pos_onlineshop.hybrid.cashierSessions;

import com.pos_onlineshop.hybrid.cashier.Cashier;
import com.pos_onlineshop.hybrid.enums.SessionStatus;
import com.pos_onlineshop.hybrid.shop.Shop;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cashier_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CashierSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "cashier_id", nullable = false)
    private Cashier cashier;

    @ManyToOne
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @Column(name = "terminal_id")
    private String terminalId; // POS terminal identifier

    @Column(name = "session_start", nullable = false)
    private LocalDateTime sessionStart;

    @Column(name = "session_end")
    private LocalDateTime sessionEnd;

    @Column(name = "opening_cash", precision = 19, scale = 2)
    private BigDecimal openingCash; // Starting cash in drawer

    @Column(name = "closing_cash", precision = 19, scale = 2)
    private BigDecimal closingCash; // Ending cash in drawer

    @Column(name = "expected_cash", precision = 19, scale = 2)
    private BigDecimal expectedCash; // Calculated based on transactions

    @Column(name = "cash_difference", precision = 19, scale = 2)
    private BigDecimal cashDifference; // Actual - Expected

    @Column(name = "total_sales", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal totalSales = BigDecimal.ZERO;

    @Column(name = "transaction_count")
    @Builder.Default
    private Integer transactionCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SessionStatus status = SessionStatus.ACTIVE;

    @Column(length = 1000)
    private String notes;

    public boolean isActive() {
        return status == SessionStatus.ACTIVE;
    }

    public void endSession(BigDecimal closingCash) {
        this.sessionEnd = LocalDateTime.now();
        this.closingCash = closingCash;
        this.cashDifference = closingCash.subtract(expectedCash);
        this.status = SessionStatus.CLOSED;
    }
}