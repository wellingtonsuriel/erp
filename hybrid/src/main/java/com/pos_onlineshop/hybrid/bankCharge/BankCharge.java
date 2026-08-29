package com.pos_onlineshop.hybrid.bankCharge;

import com.pos_onlineshop.hybrid.bankAccount.BankAccount;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A fee the bank deducted directly from a BankAccount - unlike CashBankTransfer, the
 * contra side is an expense account (5500 Bank Charges), not another tracked account.
 * Posted immediately on creation (Dr 5500 / Cr bankAccount.glAccountCode) - see
 * BankChargeService, the same "one action, one transaction" pattern as Expense.
 */
@Entity
@Table(name = "bank_charges")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"bankAccount", "currency", "createdBy", "journalEntry"})
@ToString(exclude = {"bankAccount", "currency", "createdBy", "journalEntry"})
public class BankCharge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference_number", nullable = false, unique = true, length = 60)
    private String referenceNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_account_id", nullable = false)
    private BankAccount bankAccount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    @Column(name = "charge_date", nullable = false)
    private LocalDate chargeDate;

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
