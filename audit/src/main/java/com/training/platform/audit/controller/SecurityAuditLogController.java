package com.training.platform.audit.controller;

import com.training.platform.audit.entity.AuditOutcome;
import com.training.platform.audit.entity.SecurityAuditLog;
import com.training.platform.audit.service.SecurityAuditLogService;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit/security")
public class SecurityAuditLogController extends AuditControllerSupport {
    private final SecurityAuditLogService service;

    public SecurityAuditLogController(SecurityAuditLogService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SecurityAuditLog record(@RequestBody SecurityAuditLog auditLog) {
        return service.record(auditLog);
    }

    @GetMapping("/{auditId}")
    public ResponseEntity<SecurityAuditLog> findById(@PathVariable String auditId) {
        return response(service.findById(auditId));
    }

    @GetMapping
    public Page<SecurityAuditLog> find(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String correlationId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) AuditOutcome outcome,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        rejectCombinedFilters(userId != null, correlationId, action, outcome, from, to);
        if (userId != null) {
            return service.findByUserId(userId, pageRequest(page, size));
        }
        return findCommon(service, correlationId, action, outcome, from, to, pageRequest(page, size));
    }
}
