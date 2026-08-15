package com.training.platform.audit.service;

import com.training.platform.audit.entity.AccountAuditLog;
import com.training.platform.audit.repository.AccountAuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountAuditLogService extends AppendOnlyAuditService<AccountAuditLog> {
    private final AccountAuditLogRepository repository;

    public AccountAuditLogService(AccountAuditLogRepository repository) {
        super(repository);
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<AccountAuditLog> findByAccountId(String accountId, Pageable pageable) {
        return repository.findByAccountId(accountId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<AccountAuditLog> findByTransactionId(String transactionId, Pageable pageable) {
        return repository.findByTransactionId(transactionId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<AccountAuditLog> findByTransactionRef(String transactionRef, Pageable pageable) {
        return repository.findByTransactionRef(transactionRef, pageable);
    }
}
