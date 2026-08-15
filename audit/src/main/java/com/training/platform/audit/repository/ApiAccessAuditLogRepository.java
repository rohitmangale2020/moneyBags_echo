package com.training.platform.audit.repository;

import com.training.platform.audit.entity.ApiAccessAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ApiAccessAuditLogRepository extends AppendOnlyAuditRepository<ApiAccessAuditLog> {
    Page<ApiAccessAuditLog> findByTargetService(String targetService, Pageable pageable);

    Page<ApiAccessAuditLog> findByHttpStatus(Integer httpStatus, Pageable pageable);
}
