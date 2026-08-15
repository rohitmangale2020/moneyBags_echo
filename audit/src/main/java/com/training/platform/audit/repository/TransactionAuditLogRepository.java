package com.training.platform.audit.repository;

import com.training.platform.audit.entity.TransactionAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TransactionAuditLogRepository extends AppendOnlyAuditRepository<TransactionAuditLog> {
    Page<TransactionAuditLog> findByTransactionId(String transactionId, Pageable pageable);

    Page<TransactionAuditLog> findByTransactionRef(String transactionRef, Pageable pageable);
}
