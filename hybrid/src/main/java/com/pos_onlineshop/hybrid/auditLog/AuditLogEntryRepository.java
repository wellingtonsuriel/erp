package com.pos_onlineshop.hybrid.auditLog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogEntryRepository extends JpaRepository<AuditLogEntry, Long> {
    List<AuditLogEntry> findAllByOrderByIdDesc();

    List<AuditLogEntry> findByEntityTypeAndEntityIdOrderByIdDesc(String entityType, Long entityId);

    List<AuditLogEntry> findByEntityTypeOrderByIdDesc(String entityType);
}
