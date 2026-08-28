package com.pos_onlineshop.hybrid.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Built entirely from actual GL tax-account movements (2200 VAT Output/Payable, 1400 VAT
 * Input/Recoverable, 2100 Accounts Payable) for the period - never from a configured tax
 * rate - per the "use actual journal tax movements" rule.
 *
 * Known limitation, documented rather than hidden: this chart of accounts posts every
 * configured tax nature (VAT, Fast Food Tax, Excise Duty, Environmental Levy, Surtax - see
 * TaxNature) into the same 2200/1400 accounts; OrderLine.taxAmount sums all of a
 * SellingPrice's configured taxes without distinguishing which nature each portion belongs
 * to (see OrderLine.copyProductDetails). So outputTax/inputTax below are "all tax currently
 * collected/recoverable through these accounts," not VAT isolated from every other levy.
 * A true multi-tax-type return needs either separate GL accounts per TaxNature or a
 * nature-aware GL posting split - neither exists yet.
 *
 * exemptSales/zeroRatedSales are always null: the tax model has no EXEMPT or ZERO_RATED
 * TaxNature and no way to distinguish "no tax was configured for this line" from "this line
 * is genuinely exempt/zero-rated" - reporting 0 here would misrepresent an untracked
 * classification as a confirmed one.
 */
@Data
@Builder
public class VatReturnReport {

    private LocalDate fromDate;
    private LocalDate toDate;
    private Long shopId;

    private BigDecimal outputTax;
    private BigDecimal inputTax;
    /** outputTax - inputTax. Positive = payable to the tax authority, negative = refundable. */
    private BigDecimal netTaxPayable;

    /** Net revenue recognized in the period (every REVENUE-type account's activity) - the
     * exact base that generated outputTax, since nothing else posts to a REVENUE account
     * in this system. */
    private BigDecimal taxableSales;

    /** Gross purchases posted to Accounts Payable (2100 credit only) minus inputTax - the
     * net-of-tax purchase base. 2100 is credited exclusively by STOCK_RECEIPT and
     * PURCHASE_INVOICE, both of which debit 1400 in the same entry, so this is exact, not
     * an approximation. */
    private BigDecimal taxablePurchases;

    private BigDecimal exemptSales;
    private BigDecimal zeroRatedSales;
}
