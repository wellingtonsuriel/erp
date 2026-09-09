package com.pos_onlineshop.hybrid.auditLog;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogEntryRepository extends JpaRepository<AuditLogEntry, Long> {
    List<AuditLogEntry> findAllByOrderByIdDesc();

    /** Pageable-capped sibling of findAllByOrderByIdDesc(), used by AuditLogService.findAll()
     * to bound the admin-facing "all entries" endpoint - the log is append-only and grows
     * forever, so an unbounded query here would eventually mean an unbounded DB scan and
     * response payload. */
    List<AuditLogEntry> findAllByOrderByIdDesc(Pageable pageable);

    List<AuditLogEntry> findByEntityTypeAndEntityIdOrderByIdDesc(String entityType, Long entityId);

    List<AuditLogEntry> findByEntityTypeOrderByIdDesc(String entityType);
}
