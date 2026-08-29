package com.pos_onlineshop.hybrid.cashBankTransfer;

import com.pos_onlineshop.hybrid.bankAccount.BankAccount;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.enums.CashBankTransferType;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Money moving between two BankAccount rows - deposits (till/cash into a bank account),
 * withdrawals (bank account paying out to cash), bank-to-bank/wallet-to-wallet transfers,
 * and mobile-money settlement (wallet clearing into a bank account) are all the same
 * mechanism under the hood: Dr toAccount / Cr fromAccount for the same amount, so it always
 * balances by construction - see CashBankTransferService. transferType is a reporting label
 * only. Known limitation: both ends must be a tracked BankAccount - money entering or
 * leaving the business entirely (capital injections, owner drawings) is out of scope here.
 */
@Entity
@Table(name = "cash_bank_transfers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"fromAccount", "toAccount", "currency", "createdBy", "journalEntry"})
@ToString(exclude = {"fromAccount", "toAccount", "currency", "createdBy", "journalEntry"})
public class CashBankTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference_number", nullable = false, unique = true, length = 60)
    private String referenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_type", nullable = false)
    private CashBankTransferType transferType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_account_id", nullable = false)
    private BankAccount fromAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_account_id", nullable = false)
    private BankAccount toAccount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    @Column(name = "transfer_date", nullable = false)
    private LocalDate transferDate;

    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false)
    private UserAccount createdBy;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id")
    private JournalEntry journalEntry;
}
