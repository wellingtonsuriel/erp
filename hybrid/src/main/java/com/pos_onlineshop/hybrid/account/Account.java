package com.pos_onlineshop.hybrid.account;

import com.pos_onlineshop.hybrid.enums.AccountType;
import com.pos_onlineshop.hybrid.enums.DebitCredit;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Chart of accounts entry. A shop/location is never baked into the account itself -
 * it is carried as a dimension on JournalLine.costCenterShop instead, so the chart
 * stays small and reports can still be sliced per shop.
 */
@Entity
@Table(name = "gl_accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"parentAccount"})
@ToString(exclude = {"parentAccount"})
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(name = "normal_balance", nullable = false)
    private DebitCredit normalBalance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_account_id")
    private Account parentAccount;

    @Column(name = "is_control_account", nullable = false)
    @Builder.Default
    private boolean controlAccount = false;

    /** Marks an EXPENSE account as part of Cost of Goods Sold for P&L gross-profit
     * calculation, distinct from operating expenses. Meaningless on any other account type. */
    @Column(name = "is_cost_of_goods_sold", nullable = false)
    @Builder.Default
    private boolean costOfGoodsSold = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    /** IAS 29 classification: true for a monetary item (cash, receivables, payables - a
     * fixed claim to/from currency, never restated for inflation), false for a non-monetary
     * item (inventory, fixed assets, equity - carried at historical cost and restated using
     * a general price index under IAS 29). Meaningless for REVENUE/EXPENSE accounts, which
     * IAS 29 restates differently (at the period-average index, not a point-in-time one) -
     * see GeneralPriceIndexService's class comment for what is and is not built yet. Defaults
     * true since most of the starter chart (cash, AR, AP, VAT) is monetary. */
    @Column(name = "is_monetary", nullable = false)
    @Builder.Default
    private boolean monetary = true;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
