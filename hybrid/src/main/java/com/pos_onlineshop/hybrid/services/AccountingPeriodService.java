package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.accountingPeriod.AccountingPeriod;
import com.pos_onlineshop.hybrid.accountingPeriod.AccountingPeriodRepository;
import com.pos_onlineshop.hybrid.enums.PeriodStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/**
 * Calendar-month accounting periods. getOrCreateMonthlyPeriod exists so the system always
 * has an OPEN period to post into without a manual setup step.
 *
 * NOTE - scope limitation: closePeriod here only flips status to CLOSED (which is enough
 * for GLPostingService/JournalValidator to reject further posting into it) and records
 * who/when. It does NOT run the revenue/expense sweep into Retained Earnings described in
 * the GL design report section 2.8 ("Period close") - that closing-entry generation is
 * Phase 8 work and is not implemented in this pass.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountingPeriodService {

    private static final DateTimeFormatter PERIOD_NAME = DateTimeFormatter.ofPattern("yyyy-MM");

    private final AccountingPeriodRepository accountingPeriodRepository;

    @Transactional(readOnly = true)
    public java.util.List<AccountingPeriod> findAll() {
        return accountingPeriodRepository.findAll();
    }

    @Transactional(readOnly = true)
    public AccountingPeriod findById(Long id) {
        return accountingPeriodRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Accounting period not found: " + id));
    }

    @Transactional
    public AccountingPeriod getOrCreateMonthlyPeriod(LocalDate date) {
        return accountingPeriodRepository.findContaining(date)
                .orElseGet(() -> createMonthlyPeriod(date));
    }

    private AccountingPeriod createMonthlyPeriod(LocalDate date) {
        YearMonth ym = YearMonth.from(date);
        String name = date.format(PERIOD_NAME);
        AccountingPeriod period = AccountingPeriod.builder()
                .name(name)
                .startDate(ym.atDay(1))
                .endDate(ym.atEndOfMonth())
                .status(PeriodStatus.OPEN)
                .build();
        AccountingPeriod saved = accountingPeriodRepository.save(period);
        log.info("GL: opened accounting period {}", name);
        return saved;
    }

    @Transactional
    public AccountingPeriod closePeriod(Long periodId, String closedBy) {
        AccountingPeriod period = accountingPeriodRepository.findById(periodId)
                .orElseThrow(() -> new IllegalArgumentException("Accounting period not found: " + periodId));
        if (period.getStatus() != PeriodStatus.OPEN) {
            throw new IllegalStateException("Period " + period.getName() + " is already " + period.getStatus());
        }
        period.setStatus(PeriodStatus.CLOSED);
        period.setClosedAt(LocalDateTime.now());
        period.setClosedBy(closedBy);
        return accountingPeriodRepository.save(period);
    }

    @Transactional
    public AccountingPeriod reopenPeriod(Long periodId) {
        AccountingPeriod period = accountingPeriodRepository.findById(periodId)
                .orElseThrow(() -> new IllegalArgumentException("Accounting period not found: " + periodId));
        if (period.getStatus() == PeriodStatus.LOCKED) {
            throw new IllegalStateException("Period " + period.getName() + " is LOCKED and cannot be reopened");
        }
        period.setStatus(PeriodStatus.OPEN);
        period.setClosedAt(null);
        period.setClosedBy(null);
        return accountingPeriodRepository.save(period);
    }
}
