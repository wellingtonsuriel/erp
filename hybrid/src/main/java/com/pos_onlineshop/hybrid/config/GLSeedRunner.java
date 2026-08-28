package com.pos_onlineshop.hybrid.config;

import com.pos_onlineshop.hybrid.glNumbering.JournalNumberCounter;
import com.pos_onlineshop.hybrid.glNumbering.JournalNumberCounterRepository;
import com.pos_onlineshop.hybrid.services.AccountingPeriodService;
import com.pos_onlineshop.hybrid.services.GLAccountSeedService;
import com.pos_onlineshop.hybrid.services.GLPostingRuleSeedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Runs the GL's idempotent seed steps on every startup: chart of accounts, posting rules,
 * the journal-number counter row, and the current calendar-month accounting period.
 * Every step is safe to re-run - nothing here duplicates or overwrites existing data.
 */
@Component
@Order(100)
@RequiredArgsConstructor
@Slf4j
public class GLSeedRunner implements ApplicationRunner {

    private final GLAccountSeedService accountSeedService;
    private final GLPostingRuleSeedService postingRuleSeedService;
    private final AccountingPeriodService accountingPeriodService;
    private final JournalNumberCounterRepository journalNumberCounterRepository;

    @Override
    public void run(ApplicationArguments args) {
        accountSeedService.seed();
        postingRuleSeedService.seed();
        journalNumberCounterRepository.findById(1L)
                .orElseGet(() -> journalNumberCounterRepository.save(
                        JournalNumberCounter.builder().id(1L).lastValue(0L).build()));
        accountingPeriodService.getOrCreateMonthlyPeriod(LocalDate.now());
        log.info("GL: seed complete");
    }
}
