package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.dtos.CreateSalesReturnRequest;
import com.pos_onlineshop.hybrid.dtos.SalesReturnLineRequest;
import com.pos_onlineshop.hybrid.dtos.SalesReturnLineResponse;
import com.pos_onlineshop.hybrid.dtos.SalesReturnResponse;
import com.pos_onlineshop.hybrid.enums.GLSourceModule;
import com.pos_onlineshop.hybrid.enums.InventoryMovementType;
import com.pos_onlineshop.hybrid.enums.OrderStatus;
import com.pos_onlineshop.hybrid.enums.PaymentMethod;
import com.pos_onlineshop.hybrid.enums.SalesChannel;
import com.pos_onlineshop.hybrid.gl.ManualLineSpec;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.orderLines.OrderLine;
import com.pos_onlineshop.hybrid.orderLines.OrderLineRepository;
import com.pos_onlineshop.hybrid.orders.Order;
import com.pos_onlineshop.hybrid.orders.OrderRepository;
import com.pos_onlineshop.hybrid.salesReturn.SalesReturn;
import com.pos_onlineshop.hybrid.salesReturn.SalesReturnLine;
import com.pos_onlineshop.hybrid.salesReturn.SalesReturnLineRepository;
import com.pos_onlineshop.hybrid.salesReturn.SalesReturnRepository;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import com.pos_onlineshop.hybrid.userAccount.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Reverses a completed sale's actual accounting effect - revenue, tax, COGS, and inventory
 * - for a full or partial return, rather than posting an arbitrary new transaction. Mirrors
 * POSService.postSaleToGeneralLedger's account choices in reverse (same gross account the
 * sale credited is the one this debits) so the return always undoes exactly what the sale
 * did, at the price/cost the customer was actually charged/the business actually paid (see
 * SalesReturnLine), never at today's prices. Known limitations: only orders whose gross
 * side posted to 1010/1020 (POS or online-paid) are supported - there is no ONLINE_ORDER_UNPAID
 * (on-account) GL event yet for this to mirror; and COGS/inventory-value reversal is skipped
 * (though physical stock is still restored) when the original line's unit cost wasn't known,
 * the same "never manufacture a cost" rule the original sale follows.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SalesReturnService {

    private static final String SALES_RETURNS_ACCOUNT_CODE = "4900";
    private static final String VAT_OUTPUT_ACCOUNT_CODE = "2200";
    private static final String INVENTORY_ACCOUNT_CODE = "1200";
    private static final String COGS_ACCOUNT_CODE = "5000";

    private final SalesReturnRepository salesReturnRepository;
    private final SalesReturnLineRepository salesReturnLineRepository;
    private final OrderRepository orderRepository;
    private final OrderLineRepository orderLineRepository;
    private final UserAccountRepository userAccountRepository;
    private final AccountRepository accountRepository;
    private final ShopInventoryService shopInventoryService;
    private final GLPostingService glPostingService;
    private final CurrencyService currencyService;
    private final InventoryValuationService inventoryValuationService;

    @Transactional(readOnly = true)
    public List<SalesReturnResponse> findAll() {
        return salesReturnRepository.findAllByOrderByIdDesc().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SalesReturnResponse findById(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public SalesReturnResponse createReturn(CreateSalesReturnRequest request) {
        if (salesReturnRepository.existsByReturnNumber(request.getReturnNumber())) {
            throw new IllegalArgumentException("A sales return with number " + request.getReturnNumber() + " already exists");
        }
        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + request.getOrderId()));
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Cannot return items against a cancelled order");
        }
        UserAccount createdBy = resolveUser(request.getCreatedByUserId());

        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        boolean costKnownForAllLines = true;
        List<SalesReturnLine> lines = new ArrayList<>();

        for (SalesReturnLineRequest lineRequest : request.getLines()) {
            OrderLine orderLine = orderLineRepository.findById(lineRequest.getOrderLineId())
                    .orElseThrow(() -> new IllegalArgumentException("Order line not found: " + lineRequest.getOrderLineId()));
            if (!orderLine.getOrder().getId().equals(order.getId())) {
                throw new IllegalArgumentException("Order line " + orderLine.getId() + " does not belong to order " + order.getId());
            }
            int alreadyReturned = salesReturnLineRepository.sumQuantityReturnedByOrderLineId(orderLine.getId());
            int remaining = orderLine.getQuantity() - alreadyReturned;
            if (lineRequest.getQuantityReturned() > remaining) {
                throw new IllegalArgumentException("Cannot return " + lineRequest.getQuantityReturned()
                        + " of " + orderLine.getProductName() + " - only " + remaining + " remain returnable");
            }

            BigDecimal quantity = BigDecimal.valueOf(lineRequest.getQuantityReturned());
            BigDecimal lineGross = orderLine.getUnitPrice().multiply(quantity);
            BigDecimal lineTax = orderLine.getTaxAmount() == null ? BigDecimal.ZERO
                    : orderLine.getTaxAmount()
                            .multiply(quantity)
                            .divide(BigDecimal.valueOf(orderLine.getQuantity()), 4, RoundingMode.HALF_UP);
            BigDecimal lineCost = null;
            if (orderLine.getUnitCost() != null) {
                lineCost = orderLine.getUnitCost().multiply(quantity);
                totalCost = totalCost.add(lineCost);
            } else {
                costKnownForAllLines = false;
            }

            lines.add(SalesReturnLine.builder()
                    .orderLine(orderLine)
                    .quantityReturned(lineRequest.getQuantityReturned())
                    .unitPrice(orderLine.getUnitPrice())
                    .taxAmount(lineTax)
                    .unitCost(orderLine.getUnitCost())
                    .build());

            totalGross = totalGross.add(lineGross);
            totalTax = totalTax.add(lineTax);

            // Quantity dimension: InventoryTotal goes back up regardless of whether the cost
            // is known (the units are physically back on the shelf either way).
            shopInventoryService.addStock(order.getShop().getId(), orderLine.getProduct().getId(), lineRequest.getQuantityReturned());

            // Valuation dimension: restore a real cost layer at the price the units left at
            // (never at selling price - see the class comment), only when that cost is known.
            if (orderLine.getUnitCost() != null) {
                inventoryValuationService.restoreCostLayer(order.getShop(), orderLine.getProduct(),
                        lineRequest.getQuantityReturned(), orderLine.getUnitCost(), order.getCurrency(),
                        InventoryMovementType.SALE_RETURN,
                        "SALES_RETURN-" + request.getReturnNumber() + "-LINE-" + orderLine.getId(),
                        request.getReturnDate());
            }
        }

        SalesReturn salesReturn = SalesReturn.builder()
                .returnNumber(request.getReturnNumber())
                .order(order)
                .customer(order.getCustomer())
                .reason(request.getReason())
                .returnDate(request.getReturnDate())
                .totalRefundAmount(totalGross)
                .totalTaxReversed(totalTax)
                .totalCostReversed(costKnownForAllLines ? totalCost : null)
                .createdBy(createdBy)
                .build();
        SalesReturn savedReturn = salesReturnRepository.save(salesReturn);

        lines.forEach(line -> line.setSalesReturn(savedReturn));
        salesReturnLineRepository.saveAll(lines);

        JournalEntry entry = postToGeneralLedger(savedReturn, order, costKnownForAllLines ? totalCost : null);
        savedReturn.setJournalEntry(entry);
        SalesReturn finalReturn = salesReturnRepository.save(savedReturn);
        finalReturn.setLines(lines);

        log.info("Sales return {} against order {} posted - refund {} tax {} - GL entry #{}",
                finalReturn.getReturnNumber(), order.getId(), totalGross, totalTax, entry.getEntryNumber());
        return toResponse(finalReturn);
    }

    private JournalEntry postToGeneralLedger(SalesReturn salesReturn, Order order, BigDecimal totalCost) {
        Account salesReturns = accountRepository.findByCode(SALES_RETURNS_ACCOUNT_CODE)
                .orElseThrow(() -> new IllegalStateException("Chart of accounts is missing " + SALES_RETURNS_ACCOUNT_CODE));
        Account vatOutput = accountRepository.findByCode(VAT_OUTPUT_ACCOUNT_CODE)
                .orElseThrow(() -> new IllegalStateException("Chart of accounts is missing " + VAT_OUTPUT_ACCOUNT_CODE));
        String refundAccountCode = refundAccountCodeFor(order);
        Account refundAccount = accountRepository.findByCode(refundAccountCode)
                .orElseThrow(() -> new IllegalStateException("Chart of accounts is missing " + refundAccountCode));

        BigDecimal net = salesReturn.getTotalRefundAmount().subtract(salesReturn.getTotalTaxReversed());
        String memo = "Sales return " + salesReturn.getReturnNumber() + " against order " + order.getId()
                + " (" + salesReturn.getReason() + ")";
        Currency currency = order.getCurrency();
        List<ManualLineSpec> specs = new ArrayList<>();
        BigDecimal exchangeRate = exchangeRateToBase(currency);
        specs.add(new ManualLineSpec(salesReturns, net, BigDecimal.ZERO, currency, exchangeRate, order.getShop(), memo));
        if (salesReturn.getTotalTaxReversed().compareTo(BigDecimal.ZERO) > 0) {
            specs.add(new ManualLineSpec(vatOutput, salesReturn.getTotalTaxReversed(), BigDecimal.ZERO, currency, exchangeRate, order.getShop(), memo));
        }
        specs.add(new ManualLineSpec(refundAccount, BigDecimal.ZERO, salesReturn.getTotalRefundAmount(), currency, exchangeRate, order.getShop(), memo));
        if (totalCost != null && totalCost.compareTo(BigDecimal.ZERO) > 0) {
            Account inventory = accountRepository.findByCode(INVENTORY_ACCOUNT_CODE)
                    .orElseThrow(() -> new IllegalStateException("Chart of accounts is missing " + INVENTORY_ACCOUNT_CODE));
            Account cogs = accountRepository.findByCode(COGS_ACCOUNT_CODE)
                    .orElseThrow(() -> new IllegalStateException("Chart of accounts is missing " + COGS_ACCOUNT_CODE));
            specs.add(new ManualLineSpec(inventory, totalCost, BigDecimal.ZERO, currency, exchangeRate, order.getShop(), memo));
            specs.add(new ManualLineSpec(cogs, BigDecimal.ZERO, totalCost, currency, exchangeRate, order.getShop(), memo));
        }

        return glPostingService.postManual(
                "SALES-RETURN-" + salesReturn.getId(), salesReturn.getReturnDate(), memo,
                GLSourceModule.SYSTEM, "SALES_RETURN", salesReturn.getId(), specs, salesReturn.getCreatedBy().getUsername());
    }

    private BigDecimal exchangeRateToBase(Currency currency) {
        Currency baseCurrency = currencyService.getBaseCurrency();
        return currency == null || currency.equals(baseCurrency)
                ? BigDecimal.ONE : currencyService.getExchangeRate(currency, baseCurrency);
    }

    private String refundAccountCodeFor(Order order) {
        if (order.getSalesChannel() == SalesChannel.POS) {
            return order.getPaymentMethod() == PaymentMethod.CASH ? "1010" : "1020";
        }
        return "1020";
    }

    private UserAccount resolveUser(Long userId) {
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

    private SalesReturn findOrThrow(Long id) {
        return salesReturnRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Sales return not found: " + id));
    }

    private SalesReturnResponse toResponse(SalesReturn salesReturn) {
        return SalesReturnResponse.builder()
                .id(salesReturn.getId())
                .returnNumber(salesReturn.getReturnNumber())
                .orderId(salesReturn.getOrder().getId())
                .customerId(salesReturn.getCustomer() != null ? salesReturn.getCustomer().getId() : null)
                .customerName(salesReturn.getCustomer() != null ? salesReturn.getCustomer().getName() : null)
                .reason(salesReturn.getReason())
                .returnDate(salesReturn.getReturnDate())
                .totalRefundAmount(salesReturn.getTotalRefundAmount())
                .totalTaxReversed(salesReturn.getTotalTaxReversed())
                .totalCostReversed(salesReturn.getTotalCostReversed())
                .createdById(salesReturn.getCreatedBy() != null ? salesReturn.getCreatedBy().getId() : null)
                .createdByUsername(salesReturn.getCreatedBy() != null ? salesReturn.getCreatedBy().getUsername() : null)
                .createdAt(salesReturn.getCreatedAt())
                .journalEntryId(salesReturn.getJournalEntry() != null ? salesReturn.getJournalEntry().getId() : null)
                .journalEntryNumber(salesReturn.getJournalEntry() != null ? salesReturn.getJournalEntry().getEntryNumber() : null)
                .lines(salesReturn.getLines().stream().map(this::toLineResponse).collect(Collectors.toList()))
                .build();
    }

    private SalesReturnLineResponse toLineResponse(SalesReturnLine line) {
        return SalesReturnLineResponse.builder()
                .id(line.getId())
                .orderLineId(line.getOrderLine().getId())
                .productName(line.getOrderLine().getProductName())
                .quantityReturned(line.getQuantityReturned())
                .unitPrice(line.getUnitPrice())
                .taxAmount(line.getTaxAmount())
                .unitCost(line.getUnitCost())
                .build();
    }
}
