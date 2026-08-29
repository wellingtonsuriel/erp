package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.AuditLogResponse;
import com.pos_onlineshop.hybrid.services.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/audit-log")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('AUDIT_VIEW') or hasRole('ADMIN')")
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    public List<AuditLogResponse> list() {
        return auditLogService.findAll();
    }

    @GetMapping("/entity/{entityType}/{entityId}")
    public List<AuditLogResponse> forEntity(@PathVariable String entityType, @PathVariable Long entityId) {
        return auditLogService.findForEntity(entityType, entityId);
    }
}
