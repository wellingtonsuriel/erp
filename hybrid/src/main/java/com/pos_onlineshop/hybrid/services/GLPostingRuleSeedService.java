package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.enums.AmountSource;
import com.pos_onlineshop.hybrid.enums.DebitCredit;
import com.pos_onlineshop.hybrid.enums.FinancialEventType;
import com.pos_onlineshop.hybrid.enums.ShopRole;
import com.pos_onlineshop.hybrid.postingRule.PostingRule;
import com.pos_onlineshop.hybrid.postingRule.PostingRuleLine;
import com.pos_onlineshop.hybrid.postingRule.PostingRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the posting rules for every event type whose accounting is fully determined by
 * a single shop dimension and a gross/net/tax/cost split - see FinancialEvent.
 *
 * Deliberately NOT seeded here, with reasons (see the GL implementation summary for detail):
 *  - FX_REVALUATION: the credit/debit side is whichever foreign-currency account is being
 *    revalued, decided per invocation at period close - not a fixed account pair a static
 *    PostingRule can express.
 *  - MANUAL_ENTRY: a human picks the accounts on a manual journal; that's a UI/service
 *    concern for the (not-yet-built) manual entry workflow, not a fixed rule.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GLPostingRuleSeedService {

    private final PostingRuleRepository postingRuleRepository;
    private final AccountRepository accountRepository;

    @Transactional
    public void seed() {
        seedRule(FinancialEventType.POS_CASH_SALE, "POS cash sale",
                line("1010", DebitCredit.DEBIT, AmountSource.GROSS, 1),
                line("4000", DebitCredit.CREDIT, AmountSource.NET, 2),
                line("2200", DebitCredit.CREDIT, AmountSource.TAX, 3),
                line("5000", DebitCredit.DEBIT, AmountSource.COST, 4),
                line("1200", DebitCredit.CREDIT, AmountSource.COST, 5));

        seedRule(FinancialEventType.POS_NON_CASH_SALE, "POS EcoCash / card / mobile-money sale",
                line("1020", DebitCredit.DEBIT, AmountSource.GROSS, 1),
                line("4000", DebitCredit.CREDIT, AmountSource.NET, 2),
                line("2200", DebitCredit.CREDIT, AmountSource.TAX, 3),
                line("5000", DebitCredit.DEBIT, AmountSource.COST, 4),
                line("1200", DebitCredit.CREDIT, AmountSource.COST, 5));

        seedRule(FinancialEventType.SALE_REFUND, "Generic cash sale refund (fallback only - prefer GLPostingService.reverse of the original entry)",
                line("4900", DebitCredit.DEBIT, AmountSource.NET, 1),
                line("2200", DebitCredit.DEBIT, AmountSource.TAX, 2),
                line("1010", DebitCredit.CREDIT, AmountSource.GROSS, 3),
                line("1200", DebitCredit.DEBIT, AmountSource.COST, 4),
                line("5000", DebitCredit.CREDIT, AmountSource.COST, 5));

        // 1020 (not 1010 Cash) is the default here: an online checkout is realistically never
        // physical cash-in-hand. A genuine cash-on-delivery flow, if one exists, would need
        // its own event type/rule rather than reusing this one.
        seedRule(FinancialEventType.ONLINE_ORDER_PAID, "Online order paid at checkout (card/mobile money/online payment)",
                line("1020", DebitCredit.DEBIT, AmountSource.GROSS, 1),
                line("4010", DebitCredit.CREDIT, AmountSource.NET, 2),
                line("2200", DebitCredit.CREDIT, AmountSource.TAX, 3),
                line("5000", DebitCredit.DEBIT, AmountSource.COST, 4),
                line("1200", DebitCredit.CREDIT, AmountSource.COST, 5));

        seedRule(FinancialEventType.ONLINE_ORDER_UNPAID, "Online order on account",
                line("1100", DebitCredit.DEBIT, AmountSource.GROSS, 1),
                line("4010", DebitCredit.CREDIT, AmountSource.NET, 2),
                line("2200", DebitCredit.CREDIT, AmountSource.TAX, 3),
                line("5000", DebitCredit.DEBIT, AmountSource.COST, 4),
                line("1200", DebitCredit.CREDIT, AmountSource.COST, 5));

        seedRule(FinancialEventType.STOCK_RECEIPT, "ShopInventory stock receipt from a supplier",
                line("1200", DebitCredit.DEBIT, AmountSource.NET, 1),
                line("1400", DebitCredit.DEBIT, AmountSource.TAX, 2),
                line("2100", DebitCredit.CREDIT, AmountSource.GROSS, 3));

        seedRule(FinancialEventType.INVENTORY_TRANSFER, "Inter-shop inventory transfer receipt - no P&L impact",
                destLine("1200", DebitCredit.DEBIT, AmountSource.GROSS, 1),
                line("1200", DebitCredit.CREDIT, AmountSource.GROSS, 2));

        seedRule(FinancialEventType.DAMAGED_STOCK, "Damaged stock write-off",
                line("5100", DebitCredit.DEBIT, AmountSource.GROSS, 1),
                line("1200", DebitCredit.CREDIT, AmountSource.GROSS, 2));

        seedRule(FinancialEventType.SESSION_CASH_SHORT, "Cashier session closed with a cash shortage",
                line("2900", DebitCredit.DEBIT, AmountSource.GROSS, 1),
                line("1010", DebitCredit.CREDIT, AmountSource.GROSS, 2));

        seedRule(FinancialEventType.SESSION_CASH_OVER, "Cashier session closed with excess cash",
                line("1010", DebitCredit.DEBIT, AmountSource.GROSS, 1),
                line("2900", DebitCredit.CREDIT, AmountSource.GROSS, 2));

        seedRule(FinancialEventType.LOYALTY_REDEMPTION, "Loyalty points redeemed against a sale",
                line("2300", DebitCredit.DEBIT, AmountSource.GROSS, 1),
                line("4000", DebitCredit.CREDIT, AmountSource.GROSS, 2));

        // Only used for a SupplierInvoice with no linked PurchaseOrder - see
        // SupplierInvoiceService's class comment for why PO-linked invoices don't post here too.
        seedRule(FinancialEventType.PURCHASE_INVOICE, "Supplier invoice not linked to a purchase order (service/expense)",
                line("5300", DebitCredit.DEBIT, AmountSource.NET, 1),
                line("1400", DebitCredit.DEBIT, AmountSource.TAX, 2),
                line("2100", DebitCredit.CREDIT, AmountSource.GROSS, 3));

        seedRule(FinancialEventType.SUPPLIER_PAYMENT, "Payment issued against a supplier invoice via bank",
                line("2100", DebitCredit.DEBIT, AmountSource.GROSS, 1),
                line("1030", DebitCredit.CREDIT, AmountSource.GROSS, 2));

        seedRule(FinancialEventType.SUPPLIER_PAYMENT_CASH, "Payment issued against a supplier invoice from the cash drawer",
                line("2100", DebitCredit.DEBIT, AmountSource.GROSS, 1),
                line("1010", DebitCredit.CREDIT, AmountSource.GROSS, 2));

        seedRule(FinancialEventType.CUSTOMER_INVOICE, "Standalone credit-sale invoice",
                line("1100", DebitCredit.DEBIT, AmountSource.GROSS, 1),
                line("4020", DebitCredit.CREDIT, AmountSource.NET, 2),
                line("2200", DebitCredit.CREDIT, AmountSource.TAX, 3));

        seedRule(FinancialEventType.CUSTOMER_RECEIPT, "Customer receipt via mobile money/card clearing",
                line("1020", DebitCredit.DEBIT, AmountSource.GROSS, 1),
                line("1100", DebitCredit.CREDIT, AmountSource.GROSS, 2));

        seedRule(FinancialEventType.CUSTOMER_RECEIPT_CASH, "Customer receipt via physical cash",
                line("1010", DebitCredit.DEBIT, AmountSource.GROSS, 1),
                line("1100", DebitCredit.CREDIT, AmountSource.GROSS, 2));

        seedRule(FinancialEventType.CUSTOMER_CREDIT_NOTE, "Credit note against a customer invoice",
                line("4900", DebitCredit.DEBIT, AmountSource.GROSS, 1),
                line("1100", DebitCredit.CREDIT, AmountSource.GROSS, 2));
    }

    private record LineSpec(String accountCode, DebitCredit side, AmountSource source, int sequence, ShopRole shopRole) {
    }

    private LineSpec line(String accountCode, DebitCredit side, AmountSource source, int sequence) {
        return new LineSpec(accountCode, side, source, sequence, ShopRole.SOURCE);
    }

    /** Same as line(), but its costCenter comes from FinancialEvent.destinationShop. */
    private LineSpec destLine(String accountCode, DebitCredit side, AmountSource source, int sequence) {
        return new LineSpec(accountCode, side, source, sequence, ShopRole.DESTINATION);
    }

    private void seedRule(FinancialEventType eventType, String description, LineSpec... lines) {
        if (postingRuleRepository.existsByEventType(eventType)) {
            return;
        }
        PostingRule rule = PostingRule.builder()
                .eventType(eventType)
                .active(true)
                .description(description)
                .build();
        for (LineSpec spec : lines) {
            Account account = accountRepository.findByCode(spec.accountCode())
                    .orElseThrow(() -> new IllegalStateException(
                            "GL seed error: account " + spec.accountCode() + " must be seeded before posting rules"));
            rule.addLine(PostingRuleLine.builder()
                    .account(account)
                    .side(spec.side())
                    .amountSource(spec.source())
                    .sequence(spec.sequence())
                    .shopRole(spec.shopRole())
                    .build());
        }
        postingRuleRepository.save(rule);
        log.info("GL: seeded posting rule for {}", eventType);
    }
}
