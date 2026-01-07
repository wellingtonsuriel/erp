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
import com.pos_onlineshop.hybrid.services.UserAccountService;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import lombok.Data;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/pos")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class POSController {

    private final POSService posService;
    private final CashierService cashierService;

    @PostMapping("/quick-sale")
    public ResponseEntity<Order> quickSale(@RequestBody QuickSaleRequest request) {
        try {
            // Get active session for cashier
            CashierSession session = cashierService.getActiveSession(request.getCashierId())
                    .orElseThrow(() -> new RuntimeException("No active session for cashier"));

            Order order = posService.processQuickSale(
                    request.getItems(),
                    request.getPaymentMethod(),
                    request.getCashGiven(),
                    session
            );

            // Update session with sale
            if (request.getPaymentMethod() == PaymentMethod.CASH) {
                cashierService.recordSale(session, order.getTotalAmount());
            }

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
    public ResponseEntity<Void> openCashDrawer(@RequestBody CashDrawerRequest request) {
        Cashier cashier = cashierService.findById(request.getCashierId())
                .orElseThrow(() -> new RuntimeException("Cashier not found"));

        if (!cashierService.hasPermission(cashier, Permission.OPEN_CASH_DRAWER)) {
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
            @RequestBody VoidTransactionRequest request) {
        Cashier cashier = cashierService.findById(request.getCashierId())
                .orElseThrow(() -> new RuntimeException("Cashier not found"));

        if (!cashierService.hasPermission(cashier, Permission.VOID_TRANSACTION)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        posService.voidTransaction(orderId, request.getReason());
        return ResponseEntity.ok().build();
    }


}