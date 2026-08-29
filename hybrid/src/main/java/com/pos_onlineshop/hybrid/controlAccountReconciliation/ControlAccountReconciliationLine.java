package com.pos_onlineshop.hybrid.controlAccountReconciliation;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One control account's result within a ControlAccountReconciliationRun - a frozen copy of
 * ControlAccountReconciliationReport.Line at the time the run was taken, plus the
 * resolution fields the stateless report has no way to carry: a variance found here is
 * investigated and either explained (a known, accepted timing difference) or fixed
 * elsewhere (a posting bug corrected by a real GL entry, at which point the NEXT run
 * should show it matched) - resolving this line records which happened and why, without
 * ever changing glBalance/subledgerBalance/variance themselves after the fact.
 */
@Entity
@Table(name = "control_account_reconciliation_lines")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"run"})
@ToString(exclude = {"run"})
public class ControlAccountReconciliationLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    private ControlAccountReconciliationRun run;

    @Column(name = "account_code", nullable = false, length = 20)
    private String accountCode;

    @Column(name = "account_name", nullable = false, length = 150)
    private String accountName;

    @Column(name = "subledger_name", nullable = false, length = 200)
    private String subledgerName;

    @Column(name = "gl_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal glBalance;

    @Column(name = "subledger_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal subledgerBalance;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal variance;

    @Column(nullable = false)
    private boolean matched;

    private String note;

    @Column(nullable = false)
    @Builder.Default
    private boolean resolved = false;

    @Column(name = "resolution_reason")
    private String resolutionReason;

    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
}
