package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.enums.AccountType;
import com.pos_onlineshop.hybrid.enums.DebitCredit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.pos_onlineshop.hybrid.enums.AccountType.*;
import static com.pos_onlineshop.hybrid.enums.DebitCredit.CREDIT;
import static com.pos_onlineshop.hybrid.enums.DebitCredit.DEBIT;

/**
 * Idempotently seeds the starter chart of accounts sized to what this business already
 * does (see the GL design report, section 2.2). Safe to run on every startup - accounts
 * are matched and skipped by code, never duplicated or overwritten.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GLAccountSeedService {

    private record Seed(String code, String name, AccountType type, DebitCredit normal, boolean control,
                         boolean cogs, boolean monetary) {
        Seed(String code, String name, AccountType type, DebitCredit normal, boolean control) {
            this(code, name, type, normal, control, false, true);
        }

        Seed(String code, String name, AccountType type, DebitCredit normal, boolean control, boolean cogs) {
            this(code, name, type, normal, control, cogs, true);
        }
    }

    /** monetary=false only appears explicitly below for 1200/3000/3900 - the non-monetary
     * ASSET/EQUITY items in this starter chart, per Account.monetary's IAS 29 classification.
     * REVENUE/EXPENSE accounts are left at the default true, since IAS 29 restates them at a
     * period-average index rather than the point-in-time monetary/non-monetary split - not a
     * true "monetary" classification, and not yet built (see GeneralPriceIndexService). */
    private static final List<Seed> STARTER_CHART = List.of(
            new Seed("1010", "Cash on Hand", ASSET, DEBIT, false),
            new Seed("1020", "Mobile Money / Card Clearing", ASSET, DEBIT, false),
            new Seed("1030", "Bank", ASSET, DEBIT, false),
            new Seed("1100", "Accounts Receivable", ASSET, DEBIT, true),
            new Seed("1200", "Inventory Asset", ASSET, DEBIT, true, false, false),
            new Seed("1400", "VAT Input / Recoverable", ASSET, DEBIT, false),
            new Seed("1500", "Fixed Assets", ASSET, DEBIT, true, false, false),
            new Seed("1590", "Accumulated Depreciation", ASSET, CREDIT, true, false, false),
            new Seed("2100", "Accounts Payable", LIABILITY, CREDIT, true),
            new Seed("2200", "VAT Output / Payable", LIABILITY, CREDIT, false),
            new Seed("2300", "Customer Deposits & Loyalty Liability", LIABILITY, CREDIT, false),
            new Seed("2400", "Payroll Payable", LIABILITY, CREDIT, true),
            new Seed("2410", "Payroll Deductions Payable", LIABILITY, CREDIT, true),
            new Seed("2900", "Cash Over / Short", EXPENSE, DEBIT, false),
            new Seed("3000", "Retained Earnings", EQUITY, CREDIT, false, false, false),
            new Seed("3900", "Opening Balance Equity", EQUITY, CREDIT, false, false, false),
            new Seed("3910", "IAS 29 Restatement Reserve", EQUITY, CREDIT, false, false, false),
            new Seed("4000", "Sales Revenue - POS", REVENUE, CREDIT, false),
            new Seed("4010", "Sales Revenue - Online", REVENUE, CREDIT, false),
            new Seed("4020", "Sales Revenue - Credit/Wholesale", REVENUE, CREDIT, false),
            new Seed("4900", "Sales Returns & Allowances", REVENUE, DEBIT, false),
            new Seed("5000", "Cost of Goods Sold", EXPENSE, DEBIT, false, true),
            new Seed("5100", "Inventory Write-off", EXPENSE, DEBIT, false),
            new Seed("5110", "Inventory Adjustment Gain / Loss", EXPENSE, DEBIT, false),
            new Seed("5200", "Salary and Wages Expense", EXPENSE, DEBIT, false),
            new Seed("5300", "Operating Expenses", EXPENSE, DEBIT, false),
            new Seed("5400", "Depreciation Expense", EXPENSE, DEBIT, false),
            new Seed("5500", "Bank Charges", EXPENSE, DEBIT, false),
            new Seed("5600", "Loyalty Program Expense", EXPENSE, DEBIT, false),
            new Seed("5900", "FX Gain / Loss", EXPENSE, DEBIT, false),
            new Seed("5950", "Gain / Loss on Disposal of Assets", EXPENSE, DEBIT, false)
    );

    private final AccountRepository accountRepository;

    @Transactional
    public void seed() {
        AtomicInteger created = new AtomicInteger();
        for (Seed s : STARTER_CHART) {
            accountRepository.findByCode(s.code()).ifPresentOrElse(
                    existing -> syncDerivedFlags(existing, s.cogs(), s.monetary()),
                    () -> {
                        accountRepository.save(Account.builder()
                                .code(s.code())
                                .name(s.name())
                                .accountType(s.type())
                                .normalBalance(s.normal())
                                .controlAccount(s.control())
                                .costOfGoodsSold(s.cogs())
                                .monetary(s.monetary())
                                .active(true)
                                .build());
                        created.incrementAndGet();
                    });
        }
        if (created.get() > 0) {
            log.info("GL: seeded {} chart-of-accounts entries", created.get());
        }
    }

    /** Existing rows are matched and left alone (name/type/control are user-editable via the
     * Account API), except costOfGoodsSold and monetary: a chart deployed before either field
     * existed should still pick up the correct classification once this seed runs again,
     * without disturbing anything else about the row. */
    private void syncDerivedFlags(Account existing, boolean expectedCogs, boolean expectedMonetary) {
        boolean changed = false;
        if (existing.isCostOfGoodsSold() != expectedCogs) {
            existing.setCostOfGoodsSold(expectedCogs);
            changed = true;
        }
        if (existing.isMonetary() != expectedMonetary) {
            existing.setMonetary(expectedMonetary);
            changed = true;
        }
        if (changed) {
            accountRepository.save(existing);
        }
    }
}
