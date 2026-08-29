package com.pos_onlineshop.hybrid.auditLog;

import com.pos_onlineshop.hybrid.enums.AuditAction;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One centralized audit record - entityType/entityId identify what changed (e.g.
 * "MANUAL_JOURNAL"/42), action is what happened to it, description is a short human-readable
 * summary. Never persist a secret, credential, token, or full request/response payload here
 * - description is written by the caller and must stay a summary, not a dump. performedBy is
 * a raw username string (not a UserAccount FK) so a SYSTEM-driven action can be audited too,
 * matching the "performedBy" convention already used by GLPostingService/FxRevaluationService/
 * Ias29RestatementService. Immutable once written - an audit log that could be edited would
 * defeat its own purpose.
 *
 * Known limitation: wired into a representative set of high-value mutation points (manual
 * journal approve/reject/post, accounting period close/reopen) rather than every mutation in
 * the codebase - see AuditLogService's class comment for exactly which callers use it today.
 */
@Entity
@Table(name = "audit_log_entries")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false, length = 60)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;

    @Column(name = "performed_by", nullable = false, length = 100)
    private String performedBy;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "occurred_at", nullable = false)
    @Builder.Default
    private LocalDateTime occurredAt = LocalDateTime.now();
}
