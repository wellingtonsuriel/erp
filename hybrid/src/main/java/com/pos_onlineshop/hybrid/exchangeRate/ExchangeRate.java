package com.pos_onlineshop.hybrid.exchangeRate;

import com.pos_onlineshop.hybrid.currency.Currency;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "exchange_rates",
        uniqueConstraints = @UniqueConstraint(columnNames = {"from_currency_id", "to_currency_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExchangeRate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "from_currency_id", nullable = false)
    private Currency fromCurrency;

    @ManyToOne
    @JoinColumn(name = "to_currency_id", nullable = false)
    private Currency toCurrency;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal rate;

    @Column(name = "effective_date", nullable = false)
    @Builder.Default
    private LocalDateTime effectiveDate = LocalDateTime.now();

    @Column(name = "expiry_date")
    private LocalDateTime expiryDate;

    @Column(name = "is_active")
    @Builder.Default
    private boolean active = true;

    @Column(name = "source")
    private String source; // e.g., "MANUAL", "API", "BANK"

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public boolean isEffective() {
        LocalDateTime now = LocalDateTime.now();
        return active &&
                now.isAfter(effectiveDate) &&
                (expiryDate == null || now.isBefore(expiryDate));
    }
}
