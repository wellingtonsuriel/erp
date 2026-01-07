package com.pos_onlineshop.hybrid.accountancyEntry;


import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.enums.EntryType;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
@Entity
@Table(name = "accountancy_entries")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountancyEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private EntryType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @ManyToOne
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    @Column(nullable = false)
    private String description;

    @Column(name = "reference_type")
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private UserAccount user;

    @Column(name = "entry_date", nullable = false)
    @Builder.Default
    private LocalDateTime entryDate = LocalDateTime.now();

    @Column(name = "accounting_period")
    private String accountingPeriod;

    // Multi-currency fields
    @Column(name = "base_amount", precision = 19, scale = 2)
    private BigDecimal baseAmount; // Amount in base currency

    @Column(name = "exchange_rate", precision = 19, scale = 6)
    private BigDecimal exchangeRate; // Rate used for conversion

    public boolean isDebit() {
        return type == EntryType.DEBIT;
    }

    public boolean isCredit() {
        return type == EntryType.CREDIT;
    }

    public static List<AccountancyEntry> createDoubleEntry(
            BigDecimal amount,
            Currency currency,
            String description,
            UserAccount user,
            String referenceType,
            Long referenceId,
            BigDecimal baseAmount,
            BigDecimal exchangeRate) {

        LocalDateTime now = LocalDateTime.now();
        String period = String.format("%d-Q%d", now.getYear(), (now.getMonthValue() - 1) / 3 + 1);

        AccountancyEntry debit = AccountancyEntry.builder()
                .type(EntryType.DEBIT)
                .amount(amount)
                .currency(currency)
                .description(description + " (Debit)")
                .user(user)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .accountingPeriod(period)
                .baseAmount(baseAmount)
                .exchangeRate(exchangeRate)
                .build();

        AccountancyEntry credit = AccountancyEntry.builder()
                .type(EntryType.CREDIT)
                .amount(amount)
                .currency(currency)
                .description(description + " (Credit)")
                .user(user)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .accountingPeriod(period)
                .baseAmount(baseAmount)
                .exchangeRate(exchangeRate)
                .build();

        return List.of(debit, credit);
    }
}