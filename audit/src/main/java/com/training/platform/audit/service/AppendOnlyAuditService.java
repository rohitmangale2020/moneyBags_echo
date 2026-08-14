package com.training.platform.audit.service;

import com.training.platform.audit.entity.AuditOutcome;
import com.training.platform.audit.entity.BaseAuditLog;
import com.training.platform.audit.repository.AppendOnlyAuditRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

/** Shared insert-and-read behavior; audit records cannot be updated or deleted. */
public abstract class AppendOnlyAuditService<T extends BaseAuditLog> {
    private final AppendOnlyAuditRepository<T> repository;

    protected AppendOnlyAuditService(AppendOnlyAuditRepository<T> repository) {
        this.repository = repository;
    }

    @Transactional
    public T record(T auditLog) {
        requireText(auditLog.getCorrelationId(), "correlationId");
        requireText(auditLog.getAction(), "action");
        if (auditLog.getActorType() == null) {
            throw new IllegalArgumentException("actorType is required");
        }
        if (auditLog.getOutcome() == null) {
            throw new IllegalArgumentException("outcome is required");
        }
        if (auditLog.getAuditId() != null) {
            Optional<T> previouslyStored = repository.findById(auditLog.getAuditId());
            if (previouslyStored.isPresent()) {
                return previouslyStored.get();
            }
        }
        return repository.save(auditLog);
    }

    @Transactional(readOnly = true)
    public Optional<T> findById(String auditId) {
        return repository.findById(auditId);
    }

    @Transactional(readOnly = true)
    public Page<T> findAll(Pageable pageable) {
        return repository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<T> findByCorrelationId(String correlationId, Pageable pageable) {
        return repository.findByCorrelationId(correlationId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<T> findByAction(String action, Pageable pageable) {
        return repository.findByAction(action, pageable);
    }

    @Transactional(readOnly = true)
    public Page<T> findByOutcome(AuditOutcome outcome, Pageable pageable) {
        return repository.findByOutcome(outcome, pageable);
    }

    @Transactional(readOnly = true)
    public Page<T> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to, Pageable pageable) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to");
        }
        return repository.findByCreatedAtBetween(from, to, pageable);
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
