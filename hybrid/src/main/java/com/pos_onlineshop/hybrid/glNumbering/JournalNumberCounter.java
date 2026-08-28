package com.pos_onlineshop.hybrid.glNumbering;

import jakarta.persistence.*;
import lombok.*;

/**
 * Single-row counter used to allocate JournalEntry.entryNumber. Allocation happens
 * under a pessimistic write lock inside the same transaction that posts the entry,
 * so a rolled-back post also rolls back its number allocation - the sequence stays
 * gapless because a number is never handed out except as part of a committed post.
 */
@Entity
@Table(name = "gl_journal_number_counter")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JournalNumberCounter {

    @Id
    private Long id;

    @Column(name = "last_value", nullable = false)
    @Builder.Default
    private Long lastValue = 0L;
}
