package com.training.platform.audit.repository;

import com.training.platform.audit.entity.AccountAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AccountAuditLogRepository extends AppendOnlyAuditRepository<AccountAuditLog> {
    Page<AccountAuditLog> findByAccountId(String accountId, Pageable pageable);

    Page<AccountAuditLog> findByTransactionId(String transactionId, Pageable pageable);

    Page<AccountAuditLog> findByTransactionRef(String transactionRef, Pageable pageable);
}
