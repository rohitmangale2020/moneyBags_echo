package com.training.platform.audit.repository;

import com.training.platform.audit.entity.CustomerAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CustomerAuditLogRepository extends AppendOnlyAuditRepository<CustomerAuditLog> {
    Page<CustomerAuditLog> findByCustomerId(Long customerId, Pageable pageable);
}
