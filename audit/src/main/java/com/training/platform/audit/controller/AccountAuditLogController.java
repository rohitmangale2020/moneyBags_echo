package com.training.platform.audit.controller;

import com.training.platform.audit.entity.AccountAuditLog;
import com.training.platform.audit.entity.AuditOutcome;
import com.training.platform.audit.service.AccountAuditLogService;
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
@RequestMapping("/api/audit/accounts")
public class AccountAuditLogController extends AuditControllerSupport {
    private final AccountAuditLogService service;

    public AccountAuditLogController(AccountAuditLogService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountAuditLog record(@RequestBody AccountAuditLog auditLog) {
        return service.record(auditLog);
    }

    @GetMapping("/{auditId}")
    public ResponseEntity<AccountAuditLog> findById(@PathVariable String auditId) {
        return response(service.findById(auditId));
    }

    @GetMapping
    public Page<AccountAuditLog> find(
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) String transactionId,
            @RequestParam(required = false) String transactionRef,
            @RequestParam(required = false) String correlationId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) AuditOutcome outcome,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int specificFilters = (accountId == null ? 0 : 1)
                + (transactionId == null ? 0 : 1)
                + (transactionRef == null ? 0 : 1);
        if (specificFilters > 1) {
            throw new IllegalArgumentException("Use only one audit filter at a time");
        }
        boolean specificFilter = specificFilters == 1;
        rejectCombinedFilters(specificFilter, correlationId, action, outcome, from, to);
        if (accountId != null) {
            return service.findByAccountId(accountId, pageRequest(page, size));
        }
        if (transactionId != null) {
            return service.findByTransactionId(transactionId, pageRequest(page, size));
        }
        if (transactionRef != null) {
            return service.findByTransactionRef(transactionRef, pageRequest(page, size));
        }
        return findCommon(service, correlationId, action, outcome, from, to, pageRequest(page, size));
    }
}
