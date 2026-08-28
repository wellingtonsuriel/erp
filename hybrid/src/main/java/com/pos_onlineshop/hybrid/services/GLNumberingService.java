package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.glNumbering.JournalNumberCounter;
import com.pos_onlineshop.hybrid.glNumbering.JournalNumberCounterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Allocates sequential, gapless JournalEntry numbers. The counter row is locked
 * PESSIMISTIC_WRITE and incremented inside the caller's own posting transaction
 * (Propagation.MANDATORY - this must never run on its own transaction), so a rolled
 * back post also rolls back the number it took: no number is ever consumed without
 * a corresponding committed journal entry.
 *
 * This deliberately serializes journal posting on a single row. That's an accepted
 * tradeoff for gapless numbering; it is not the highest-QPS path in this system.
 */
@Service
@RequiredArgsConstructor
public class GLNumberingService {

    private static final Long COUNTER_ID = 1L;

    private final JournalNumberCounterRepository counterRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public Long nextEntryNumber() {
        JournalNumberCounter counter = counterRepository.findByIdForUpdate(COUNTER_ID)
                .orElseGet(() -> counterRepository.save(
                        JournalNumberCounter.builder().id(COUNTER_ID).lastValue(0L).build()));
        counter.setLastValue(counter.getLastValue() + 1);
        counterRepository.save(counter);
        return counter.getLastValue();
    }
}
