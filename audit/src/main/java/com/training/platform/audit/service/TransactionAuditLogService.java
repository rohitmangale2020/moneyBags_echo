package com.training.platform.audit.service;

import com.training.platform.audit.entity.TransactionAuditLog;
import com.training.platform.audit.repository.TransactionAuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionAuditLogService extends AppendOnlyAuditService<TransactionAuditLog> {
    private final TransactionAuditLogRepository repository;

    public TransactionAuditLogService(TransactionAuditLogRepository repository) {
        super(repository);
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<TransactionAuditLog> findByTransactionId(String transactionId, Pageable pageable) {
        return repository.findByTransactionId(transactionId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<TransactionAuditLog> findByTransactionRef(String transactionRef, Pageable pageable) {
        return repository.findByTransactionRef(transactionRef, pageable);
    }
}
