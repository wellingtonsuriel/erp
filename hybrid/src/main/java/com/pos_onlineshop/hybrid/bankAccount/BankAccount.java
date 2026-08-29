package com.pos_onlineshop.hybrid.bankAccount;

import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.enums.CashBankAccountType;
import com.pos_onlineshop.hybrid.shop.Shop;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A named cash/bank/mobile-money account - the subledger the flat control accounts
 * (1010 Cash on Hand, 1020 Mobile Money / Card Clearing, 1030 Bank) never had on their own.
 * Multiple BankAccount rows can (and normally do) share one glAccountCode - e.g. two
 * different bank accounts both roll up to 1030 - the same many-subledger-rows-to-one-
 * control-account pattern as ExpenseCategory/FixedAssetCategory. currentBalance is
 * maintained by CashBankTransferService/BankChargeService as each transaction posts, not
 * recomputed from the GL - it exists to answer "how much is in this specific account" fast,
 * and is reconciled to its control account's GL balance externally, not by this field.
 */
@Entity
@Table(name = "bank_accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"currency", "shop"})
@ToString(exclude = {"currency", "shop"})
public class BankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_name", nullable = false, unique = true, length = 150)
    private String accountName;

    @Column(name = "account_number", length = 60)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    private CashBankAccountType accountType;

    @Column(name = "gl_account_code", nullable = false, length = 20)
    private String glAccountCode;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id")
    private Shop shop;

    @Column(name = "opening_balance", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal openingBalance = BigDecimal.ZERO;

    @Column(name = "current_balance", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal currentBalance = BigDecimal.ZERO;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public static String defaultGlAccountCode(CashBankAccountType type) {
        return switch (type) {
            case CASH -> "1010";
            case MOBILE_MONEY -> "1020";
            case BANK -> "1030";
        };
    }
}
