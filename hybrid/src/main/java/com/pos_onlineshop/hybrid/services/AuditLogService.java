package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.auditLog.AuditLogEntry;
import com.pos_onlineshop.hybrid.auditLog.AuditLogEntryRepository;
import com.pos_onlineshop.hybrid.dtos.AuditLogResponse;
import com.pos_onlineshop.hybrid.enums.AuditAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Centralized audit trail. Callers today: ManualJournalService (approve/reject/post) and
 * AccountingPeriodService (close/reopen) - the clearest "approvals/journal changes/period
 * changes" cases the master build's audit requirement names. Known limitation: not wired
 * into every mutation across the codebase (payments, invoices, PO approvals, price changes,
 * inventory adjustments) - record() is a one-line call any of those services can add when
 * that coverage is needed, but retrofitting all of them in one pass risked spreading this
 * change too thin across unrelated modules without adding proportional value yet.
 *
 * record() never accepts anything beyond a short description string - there is no payload
 * field for a caller to accidentally dump a request body (and any secret it might contain)
 * into. Entries are never updated or deleted once written.
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogEntryRepository auditLogEntryRepository;

    public void record(String entityType, Long entityId, AuditAction action, String performedBy, String description) {
        auditLogEntryRepository.save(AuditLogEntry.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .performedBy(performedBy)
                .description(description)
                .build());
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> findAll() {
        return auditLogEntryRepository.findAllByOrderByIdDesc().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> findForEntity(String entityType, Long entityId) {
        return auditLogEntryRepository.findByEntityTypeAndEntityIdOrderByIdDesc(entityType, entityId).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    private AuditLogResponse toResponse(AuditLogEntry entry) {
        return AuditLogResponse.builder()
                .id(entry.getId())
                .entityType(entry.getEntityType())
                .entityId(entry.getEntityId())
                .action(entry.getAction().name())
                .performedBy(entry.getPerformedBy())
                .description(entry.getDescription())
                .occurredAt(entry.getOccurredAt())
                .build();
    }
}
