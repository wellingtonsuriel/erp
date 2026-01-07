package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.accountancyEntry.AccountancyEntry;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.currency.CurrencyRepository;
import com.pos_onlineshop.hybrid.dtos.CreateEntryRequest;
import com.pos_onlineshop.hybrid.enums.EntryType;
import com.pos_onlineshop.hybrid.services.AccountancyService;
import com.pos_onlineshop.hybrid.services.UserAccountService;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/accountancy")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AccountancyController {

    private final AccountancyService accountancyService;
    private final UserAccountService userAccountService;

    @GetMapping("/entries")
    @PreAuthorize("hasRole('ADMIN')")
    public List<AccountancyEntry> getEntries(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        return accountancyService.findByDateRange(startDate, endDate);
    }

    @GetMapping("/my-entries")
    @PreAuthorize("hasRole('USER')")
    public Page<AccountancyEntry> getMyEntries(
            @AuthenticationPrincipal UserDetails userDetails,
            Pageable pageable) {
        UserAccount user = userAccountService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        return accountancyService.findByUser(user, pageable);
    }

    @GetMapping("/summary/periodic")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Object[]> getPeriodicSummary() {
        return accountancyService.getPeriodicSummary();
    }

    @GetMapping("/balance/{period}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BigDecimal> getBalanceForPeriod(@PathVariable String period, Currency currency) {
        BigDecimal balance = accountancyService.getBalanceForPeriod(period,currency);
        return ResponseEntity.ok(balance != null ? balance : BigDecimal.ZERO);
    }

    @GetMapping("/summary/by-type")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, List<Object[]>> getSummaryByType() {
        return Map.of(
                "debit", accountancyService.getSummaryByType(EntryType.DEBIT),
                "credit", accountancyService.getSummaryByType(EntryType.CREDIT)
        );
    }

    @PostMapping("/entries")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AccountancyEntry> createEntry(
            @RequestBody CreateEntryRequest request) {
        UserAccount user = request.getUserId() != null ?
                userAccountService.findById(request.getUserId())
                        .orElseThrow(() -> new RuntimeException("User not found")) : null;

        AccountancyEntry entry = accountancyService.createEntry(
                request.getType(),
                request.getAmount(),
                request.getCurrency(),
                request.getDescription(),
                user,
                request.getReferenceType(),
                request.getReferenceId()
        );

        return ResponseEntity.ok(entry);
    }


}