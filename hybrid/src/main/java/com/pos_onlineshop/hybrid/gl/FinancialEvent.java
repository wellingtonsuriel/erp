package com.pos_onlineshop.hybrid.gl;

import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.enums.FinancialEventType;
import com.pos_onlineshop.hybrid.enums.GLSourceModule;
import com.pos_onlineshop.hybrid.shop.Shop;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What a subledger module (POS, Inventory, Tax, ...) hands to the GLPostingService.
 * The subledger never names an account - it describes what happened and how much
 * money moved, sliced into gross/net/tax/cost; the PostingRule for eventType decides
 * which accounts each slice lands in.
 *
 * All four amount fields are in the same transaction currency. Any of net/tax/cost may
 * be null when not applicable to this event - the corresponding PostingRuleLine(s) are
 * then skipped rather than posting a zero line.
 */
@Data
@Builder
public class FinancialEvent {

    private FinancialEventType eventType;
    private GLSourceModule sourceModule;
    private String sourceReferenceType;
    private Long sourceReferenceId;

    /** Idempotency key, e.g. "POS-SALE-{orderId}". Required - the engine will not guess one. */
    private String idempotencyKey;

    private LocalDate eventDate;
    private String description;

    /** Primary shop dimension - used by every event type. */
    private Shop shop;
    /** Only meaningful for INVENTORY_TRANSFER: the shop stock is moving into. */
    private Shop destinationShop;
    private Currency currency;
    private BigDecimal exchangeRate;
    private BigDecimal baseAmount;

    /** Full amount including tax, e.g. what a cash customer physically hands over. */
    private BigDecimal grossAmount;
    /** Amount excluding tax - what actually counts as revenue. */
    private BigDecimal netAmount;
    /** Tax component of grossAmount. */
    private BigDecimal taxAmount;
    /** Cost of goods sold for this event, if known (null when cost data isn't available). */
    private BigDecimal costAmount;

    private String postedBy;
}
