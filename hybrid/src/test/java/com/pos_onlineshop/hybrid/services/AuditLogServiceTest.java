package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.auditLog.AuditLogEntry;
import com.pos_onlineshop.hybrid.auditLog.AuditLogEntryRepository;
import com.pos_onlineshop.hybrid.dtos.AuditLogResponse;
import com.pos_onlineshop.hybrid.enums.AuditAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock private AuditLogEntryRepository auditLogEntryRepository;

    private AuditLogService service;

    @BeforeEach
    void setUp() {
        service = new AuditLogService(auditLogEntryRepository);
    }

    @Test
    void recordSavesAnAuditEntryWithEveryField() {
        when(auditLogEntryRepository.save(any(AuditLogEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        service.record("MANUAL_JOURNAL", 42L, AuditAction.APPROVE, "admin1", "Manual journal approved");

        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(auditLogEntryRepository).save(captor.capture());
        AuditLogEntry saved = captor.getValue();
        assertEquals("MANUAL_JOURNAL", saved.getEntityType());
        assertEquals(42L, saved.getEntityId());
        assertEquals(AuditAction.APPROVE, saved.getAction());
        assertEquals("admin1", saved.getPerformedBy());
        assertEquals("Manual journal approved", saved.getDescription());
    }

    @Test
    void findForEntityReturnsOnlyThatEntitysEntries() {
        AuditLogEntry entry = AuditLogEntry.builder().id(1L).entityType("MANUAL_JOURNAL").entityId(42L)
                .action(AuditAction.APPROVE).performedBy("admin1").description("Approved").build();
        when(auditLogEntryRepository.findByEntityTypeAndEntityIdOrderByIdDesc("MANUAL_JOURNAL", 42L))
                .thenReturn(List.of(entry));

        List<AuditLogResponse> results = service.findForEntity("MANUAL_JOURNAL", 42L);

        assertEquals(1, results.size());
        assertEquals("APPROVE", results.get(0).getAction());
    }
}
