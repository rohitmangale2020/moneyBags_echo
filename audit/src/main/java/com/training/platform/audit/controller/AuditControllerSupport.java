package com.training.platform.audit.controller;

import com.training.platform.audit.entity.AuditOutcome;
import com.training.platform.audit.entity.BaseAuditLog;
import com.training.platform.audit.service.AppendOnlyAuditService;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;

abstract class AuditControllerSupport {
    protected Pageable pageRequest(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        return PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    protected <T> ResponseEntity<T> response(Optional<T> result) {
        return result.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    protected boolean hasCommonFilter(String correlationId, String action, AuditOutcome outcome,
                                      LocalDateTime from, LocalDateTime to) {
        return correlationId != null || action != null || outcome != null || from != null || to != null;
    }

    protected <T extends BaseAuditLog> Page<T> findCommon(
            AppendOnlyAuditService<T> service,
            String correlationId,
            String action,
            AuditOutcome outcome,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable) {
        int filters = (correlationId == null ? 0 : 1)
                + (action == null ? 0 : 1)
                + (outcome == null ? 0 : 1)
                + (from == null && to == null ? 0 : 1);
        if (filters > 1) {
            throw new IllegalArgumentException("Use only one audit filter at a time");
        }
        if ((from == null) != (to == null)) {
            throw new IllegalArgumentException("Both from and to are required for a date range");
        }
        if (correlationId != null) {
            return service.findByCorrelationId(correlationId, pageable);
        }
        if (action != null) {
            return service.findByAction(action, pageable);
        }
        if (outcome != null) {
            return service.findByOutcome(outcome, pageable);
        }
        if (from != null) {
            return service.findByCreatedAtBetween(from, to, pageable);
        }
        return service.findAll(pageable);
    }

    protected void rejectCombinedFilters(boolean specificFilter, String correlationId, String action,
                                         AuditOutcome outcome, LocalDateTime from, LocalDateTime to) {
        if (specificFilter && hasCommonFilter(correlationId, action, outcome, from, to)) {
            throw new IllegalArgumentException("Use only one audit filter at a time");
        }
    }
}
