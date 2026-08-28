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

    private record Seed(String code, String name, AccountType type, DebitCredit normal, boolean control, boolean cogs) {
        Seed(String code, String name, AccountType type, DebitCredit normal, boolean control) {
            this(code, name, type, normal, control, false);
        }
    }

    private static final List<Seed> STARTER_CHART = List.of(
            new Seed("1010", "Cash on Hand", ASSET, DEBIT, false),
            new Seed("1020", "Mobile Money / Card Clearing", ASSET, DEBIT, false),
            new Seed("1030", "Bank", ASSET, DEBIT, false),
            new Seed("1100", "Accounts Receivable", ASSET, DEBIT, true),
            new Seed("1200", "Inventory Asset", ASSET, DEBIT, true),
            new Seed("1400", "VAT Input / Recoverable", ASSET, DEBIT, false),
            new Seed("2100", "Accounts Payable", LIABILITY, CREDIT, true),
            new Seed("2200", "VAT Output / Payable", LIABILITY, CREDIT, false),
            new Seed("2300", "Customer Deposits & Loyalty Liability", LIABILITY, CREDIT, false),
            new Seed("2900", "Cash Over / Short", EXPENSE, DEBIT, false),
            new Seed("3000", "Retained Earnings", EQUITY, CREDIT, false),
            new Seed("3900", "Opening Balance Equity", EQUITY, CREDIT, false),
            new Seed("4000", "Sales Revenue - POS", REVENUE, CREDIT, false),
            new Seed("4010", "Sales Revenue - Online", REVENUE, CREDIT, false),
            new Seed("4020", "Sales Revenue - Credit/Wholesale", REVENUE, CREDIT, false),
            new Seed("4900", "Sales Returns & Allowances", REVENUE, DEBIT, false),
            new Seed("5000", "Cost of Goods Sold", EXPENSE, DEBIT, false, true),
            new Seed("5100", "Inventory Write-off", EXPENSE, DEBIT, false),
            new Seed("5300", "Operating Expenses", EXPENSE, DEBIT, false),
            new Seed("5900", "FX Gain / Loss", EXPENSE, DEBIT, false)
    );

    private final AccountRepository accountRepository;

    @Transactional
    public void seed() {
        AtomicInteger created = new AtomicInteger();
        for (Seed s : STARTER_CHART) {
            accountRepository.findByCode(s.code()).ifPresentOrElse(
                    existing -> syncCostOfGoodsSoldFlag(existing, s.cogs()),
                    () -> {
                        accountRepository.save(Account.builder()
                                .code(s.code())
                                .name(s.name())
                                .accountType(s.type())
                                .normalBalance(s.normal())
                                .controlAccount(s.control())
                                .costOfGoodsSold(s.cogs())
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
     * Account API), except costOfGoodsSold: a chart deployed before that field existed should
     * still classify 5000 as COGS once this seed runs again, without disturbing anything else
     * about the row. */
    private void syncCostOfGoodsSoldFlag(Account existing, boolean expectedCogs) {
        if (existing.isCostOfGoodsSold() != expectedCogs) {
            existing.setCostOfGoodsSold(expectedCogs);
            accountRepository.save(existing);
        }
    }
}
