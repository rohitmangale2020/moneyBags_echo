package com.training.platform.audit.repository;

import com.training.platform.audit.entity.SecurityAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SecurityAuditLogRepository extends AppendOnlyAuditRepository<SecurityAuditLog> {
    Page<SecurityAuditLog> findByUserId(Long userId, Pageable pageable);
}
