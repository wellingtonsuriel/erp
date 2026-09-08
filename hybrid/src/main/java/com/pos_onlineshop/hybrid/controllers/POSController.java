package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.cashier.Cashier;
import com.pos_onlineshop.hybrid.cashierSessions.CashierSession;
import com.pos_onlineshop.hybrid.dtos.*;
import com.pos_onlineshop.hybrid.enums.PaymentMethod;
import com.pos_onlineshop.hybrid.enums.Permission;
import com.pos_onlineshop.hybrid.orders.Order;
import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.services.CashierService;
import com.pos_onlineshop.hybrid.services.POSService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

/**
 * The audit's P0-2 finding: every endpoint here previously had no authentication at all, and
 * quickSale/voidTransaction resolved "which cashier is acting" from a client-supplied
 * request.getCashierId() - meaning any caller could process a sale, or void one, as any cashier
 * simply by naming a different ID, with no way to detect the impersonation. Every endpoint now
 * requires an authenticated cashier (class-level @PreAuthorize("hasRole('CASHIER')") - see
 * CashierUserDetailsService for how a JWT actually gets that role), and every operation that
 * needs to know "who is really doing this" resolves it from the authenticated principal via
 * requireAuthenticatedCashier, never from the request body. A client-supplied cashierId
 * (still present in QuickSaleRequest/CashDrawerRequest/VoidTransactionRequest for backward
 * compatibility) is simply ignored for identity purposes now.
 */
@RestController
@RequestMapping("/api/pos")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@PreAuthorize("hasRole('CASHIER')")
public class POSController {

    private final POSService posService;
    private final CashierService cashierService;

    private Cashier requireAuthenticatedCashier(UserDetails principal) {
        if (principal == null) {
            throw new AccessDeniedException("Authentication required");
        }
        return cashierService.findByUsername(principal.getUsername())
                .orElseThrow(() -> new AccessDeniedException("Authenticated principal is not a cashier"));
    }

    @PostMapping("/quick-sale")
    public ResponseEntity<Order> quickSale(@RequestBody QuickSaleRequest request,
                                            @AuthenticationPrincipal UserDetails principal) {
        try {
            Cashier actingCashier = requireAuthenticatedCashier(principal);

            // The session - and therefore every downstream stock/GL effect - belongs to
            // whoever actually authenticated, never to a cashier ID named in the request body.
            CashierSession session = cashierService.getActiveSession(actingCashier.getId())
                    .orElseThrow(() -> new RuntimeException("No active session for cashier"));

            Order order = posService.processQuickSale(
                    request.getItems(),
                    request.getPaymentMethod(),
                    request.getCashGiven(),
                    session
            );

            // Update session with sale - totalSales/transactionCount for every payment method,
            // expectedCash (the physical drawer expectation) only for cash.
            cashierService.recordSale(session, order.getTotalAmount(), request.getPaymentMethod() == PaymentMethod.CASH);

            return ResponseEntity.ok(order);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/barcode-scan")
    public ResponseEntity<Product> scanProduct(@RequestBody BarcodeScanRequest request) {
        Product product = posService.findProductByBarcode(request.getBarcode());
        return ResponseEntity.ok(product);
    }

    @GetMapping("/daily-summary")
    public ResponseEntity<DailySummary> getDailySummary(
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) Long shopId) {
        DailySummary summary = posService.getDailySummary(
                date != null ? date : LocalDate.now(),
                shopId
        );
        return ResponseEntity.ok(summary);
    }

    @PostMapping("/open-cash-drawer")
    public ResponseEntity<Void> openCashDrawer(@AuthenticationPrincipal UserDetails principal) {
        Cashier actingCashier = requireAuthenticatedCashier(principal);

        if (!cashierService.hasPermission(actingCashier, Permission.OPEN_CASH_DRAWER)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        posService.openCashDrawer();
        return ResponseEntity.ok().build();
    }

    @GetMapping("/receipt/{orderId}")
    public ResponseEntity<Receipt> getReceipt(@PathVariable Long orderId) {
        Receipt receipt = posService.generateReceipt(orderId);
        return ResponseEntity.ok(receipt);
    }

    @PostMapping("/void-transaction/{orderId}")
    public ResponseEntity<Void> voidTransaction(
            @PathVariable Long orderId,
            @RequestBody VoidTransactionRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        Cashier actingCashier = requireAuthenticatedCashier(principal);

        if (!cashierService.hasPermission(actingCashier, Permission.VOID_TRANSACTION)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        posService.voidTransaction(orderId, request.getReason());
        return ResponseEntity.ok().build();
    }

}
