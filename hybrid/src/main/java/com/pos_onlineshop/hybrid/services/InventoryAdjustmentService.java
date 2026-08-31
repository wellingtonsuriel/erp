package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.dtos.CreateInventoryAdjustmentRequest;
import com.pos_onlineshop.hybrid.dtos.InventoryAdjustmentResponse;
import com.pos_onlineshop.hybrid.enums.GLSourceModule;
import com.pos_onlineshop.hybrid.enums.InventoryMovementType;
import com.pos_onlineshop.hybrid.gl.ManualLineSpec;
import com.pos_onlineshop.hybrid.inventoryAdjustment.InventoryAdjustment;
import com.pos_onlineshop.hybrid.inventoryAdjustment.InventoryAdjustmentRepository;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.products.ProductRepository;
import com.pos_onlineshop.hybrid.shop.Shop;
import com.pos_onlineshop.hybrid.shop.ShopRepository;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import com.pos_onlineshop.hybrid.userAccount.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manual, ad-hoc stock-count corrections against the one authoritative live pool
 * (InventoryTotal/ShopInventory FIFO layers) - see InventoryAdjustment's class comment. Both
 * directions go through the same InventoryValuationService every other inventory movement
 * uses, and both post to the GL via GLPostingService, so a correction always leaves the
 * quantity ledger, the FIFO valuation ledger, and the GL in agreement with each other -
 * never a quantity change with no accounting effect, and never a GL entry that doesn't
 * correspond to a real quantity change.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class InventoryAdjustmentService {

    private static final String INVENTORY_ASSET_ACCOUNT_CODE = "1200";
    private static final String ADJUSTMENT_GAIN_LOSS_ACCOUNT_CODE = "5110";

    private final InventoryAdjustmentRepository inventoryAdjustmentRepository;
    private final ShopRepository shopRepository;
    private final ProductRepository productRepository;
    private final UserAccountRepository userAccountRepository;
    private final AccountRepository accountRepository;
    private final ShopInventoryService shopInventoryService;
    private final InventoryValuationService inventoryValuationService;
    private final CurrencyService currencyService;
    private final GLPostingService glPostingService;

    public List<InventoryAdjustmentResponse> findAll() {
        return inventoryAdjustmentRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    public InventoryAdjustmentResponse createAdjustment(CreateInventoryAdjustmentRequest request, Long actingUserId) {
        InventoryAdjustment existing = inventoryAdjustmentRepository.findByReference(request.getReference()).orElse(null);
        if (existing != null) {
            log.info("Inventory adjustment replay for reference {} - returning existing adjustment #{}",
                    request.getReference(), existing.getId());
            return toResponse(existing);
        }

        if (request.getQuantityDelta() == null || request.getQuantityDelta() == 0) {
            throw new IllegalArgumentException("Quantity delta must be non-zero");
        }

        Shop shop = shopRepository.findById(request.getShopId())
                .orElseThrow(() -> new IllegalArgumentException("Shop not found: " + request.getShopId()));
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + request.getProductId()));
        UserAccount actor = userAccountRepository.findById(actingUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + actingUserId));

        int quantityDelta = request.getQuantityDelta();
        LocalDate today = LocalDate.now();
        Currency baseCurrency = currencyService.getBaseCurrency();

        BigDecimal totalValue;
        boolean fullyCosted;

        if (quantityDelta > 0) {
            if (request.getUnitCost() == null) {
                throw new IllegalArgumentException("Unit cost is required for a positive (surplus) adjustment");
            }
            shopInventoryService.addStock(shop.getId(), product.getId(), quantityDelta,
                    "Adjustment " + request.getReference() + ": " + request.getReason());
            inventoryValuationService.restoreCostLayer(shop, product, quantityDelta, request.getUnitCost(),
                    baseCurrency, InventoryMovementType.ADJUSTMENT_IN, request.getReference(), today);
            totalValue = request.getUnitCost().multiply(BigDecimal.valueOf(quantityDelta));
            fullyCosted = true;
        } else {
            int shortageQuantity = -quantityDelta;
            shopInventoryService.reduceStock(shop.getId(), product.getId(), shortageQuantity,
                    "Adjustment " + request.getReference() + ": " + request.getReason());
            InventoryValuationService.CostResult cost = inventoryValuationService.consumeCostLayers(
                    shop, product, shortageQuantity, InventoryMovementType.ADJUSTMENT_OUT, request.getReference(), today);
            totalValue = cost.getTotalCost();
            fullyCosted = cost.isFullyCosted();
            if (!fullyCosted) {
                log.warn("Inventory adjustment {} for shop {} product {}: FIFO layers only covered {} of {} "
                                + "requested units - InventoryTotal is still reduced by the full amount (the shelf "
                                + "really did lose that many units) but the GL reflects only the costed portion",
                        request.getReference(), shop.getId(), product.getId(), cost.getQuantityCosted(), shortageQuantity);
            }
        }

        InventoryAdjustment adjustment = InventoryAdjustment.builder()
                .reference(request.getReference())
                .shop(shop)
                .product(product)
                .quantityDelta(quantityDelta)
                .reason(request.getReason())
                .unitCost(quantityDelta > 0 ? request.getUnitCost() : null)
                .totalValue(totalValue)
                .createdBy(actor)
                .build();

        if (totalValue.compareTo(BigDecimal.ZERO) > 0) {
            JournalEntry entry = postToGeneralLedger(shop, product, request.getReference(), quantityDelta, totalValue,
                    baseCurrency, actor.getUsername());
            adjustment.setPostedJournalEntry(entry);
        }

        InventoryAdjustment saved = inventoryAdjustmentRepository.save(adjustment);
        InventoryAdjustmentResponse response = toResponse(saved);
        response.setFullyCosted(fullyCosted);
        return response;
    }

    private JournalEntry postToGeneralLedger(Shop shop, Product product, String reference, int quantityDelta,
                                              BigDecimal totalValue, Currency baseCurrency, String actingUsername) {
        Account inventoryAsset = accountRepository.findByCode(INVENTORY_ASSET_ACCOUNT_CODE)
                .orElseThrow(() -> new IllegalStateException("Missing seeded account " + INVENTORY_ASSET_ACCOUNT_CODE));
        Account adjustmentGainLoss = accountRepository.findByCode(ADJUSTMENT_GAIN_LOSS_ACCOUNT_CODE)
                .orElseThrow(() -> new IllegalStateException("Missing seeded account " + ADJUSTMENT_GAIN_LOSS_ACCOUNT_CODE));

        String memo = "Inventory adjustment " + reference + ": " + product.getName() + " (" + shop.getName() + ")";
        List<ManualLineSpec> specs = new ArrayList<>();
        if (quantityDelta > 0) {
            // Surplus found: Dr Inventory Asset / Cr Inventory Adjustment Gain
            specs.add(new ManualLineSpec(inventoryAsset, totalValue, BigDecimal.ZERO, baseCurrency, BigDecimal.ONE, shop, memo));
            specs.add(new ManualLineSpec(adjustmentGainLoss, BigDecimal.ZERO, totalValue, baseCurrency, BigDecimal.ONE, shop, memo));
        } else {
            // Shortage found: Dr Inventory Adjustment Loss / Cr Inventory Asset
            specs.add(new ManualLineSpec(adjustmentGainLoss, totalValue, BigDecimal.ZERO, baseCurrency, BigDecimal.ONE, shop, memo));
            specs.add(new ManualLineSpec(inventoryAsset, BigDecimal.ZERO, totalValue, baseCurrency, BigDecimal.ONE, shop, memo));
        }

        return glPostingService.postManual(
                "INVENTORY-ADJUSTMENT-" + reference, LocalDate.now(), memo,
                GLSourceModule.SYSTEM, "INVENTORY_ADJUSTMENT", null, specs, actingUsername);
    }

    private InventoryAdjustmentResponse toResponse(InventoryAdjustment adjustment) {
        return InventoryAdjustmentResponse.builder()
                .id(adjustment.getId())
                .reference(adjustment.getReference())
                .shopId(adjustment.getShop().getId())
                .shopName(adjustment.getShop().getName())
                .productId(adjustment.getProduct().getId())
                .productName(adjustment.getProduct().getName())
                .quantityDelta(adjustment.getQuantityDelta())
                .reason(adjustment.getReason())
                .unitCost(adjustment.getUnitCost())
                .totalValue(adjustment.getTotalValue())
                .fullyCosted(true)
                .createdById(adjustment.getCreatedBy().getId())
                .createdByUsername(adjustment.getCreatedBy().getUsername())
                .createdAt(adjustment.getCreatedAt())
                .postedJournalEntryId(adjustment.getPostedJournalEntry() != null ? adjustment.getPostedJournalEntry().getId() : null)
                .postedJournalEntryNumber(adjustment.getPostedJournalEntry() != null ? adjustment.getPostedJournalEntry().getEntryNumber() : null)
                .build();
    }
}
