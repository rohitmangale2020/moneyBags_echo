package com.training.platform.audit.repository;

import com.training.platform.audit.entity.AuditOutcome;
import com.training.platform.audit.entity.BaseAuditLog;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

/**
 * Deliberately exposes inserts and reads only. Audit repositories do not provide
 * delete methods. Repeated delivery of the same client-generated audit ID is
 * treated idempotently and never updates the stored row.
 */
@NoRepositoryBean
public interface AppendOnlyAuditRepository<T extends BaseAuditLog> extends Repository<T, String> {
    <S extends T> S save(S auditLog);

    Optional<T> findById(String auditId);

    Page<T> findAll(Pageable pageable);

    Page<T> findByCorrelationId(String correlationId, Pageable pageable);

    Page<T> findByAction(String action, Pageable pageable);

    Page<T> findByOutcome(AuditOutcome outcome, Pageable pageable);

    Page<T> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to, Pageable pageable);
}
