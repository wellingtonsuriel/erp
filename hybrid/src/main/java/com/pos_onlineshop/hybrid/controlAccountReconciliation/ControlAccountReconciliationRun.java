package com.pos_onlineshop.hybrid.controlAccountReconciliation;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A persisted snapshot of one ControlAccountReconciliationService.generate() run - the
 * on-demand report itself (GET /api/gl-reports/reconciliation) stays stateless and
 * unpersisted for a quick ad-hoc check, but a run triggered via
 * ControlAccountReconciliationService.runAndPersist() is saved here so "was this account
 * reconciled for this period, and what did we do about any variance" has a real, queryable
 * history instead of only existing in whichever terminal happened to call the report at the
 * time. Never edited once created - see ControlAccountReconciliationLine for how an
 * individual variance is tracked to resolution without rewriting the run itself.
 */
@Entity
@Table(name = "control_account_reconciliation_runs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"lines"})
@ToString(exclude = {"lines"})
public class ControlAccountReconciliationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "as_of_date", nullable = false)
    private LocalDate asOfDate;

    @Column(name = "performed_by", nullable = false, length = 100)
    private String performedBy;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ControlAccountReconciliationLine> lines = new ArrayList<>();
}
