package com.training.platform.audit.repository;

import com.training.platform.audit.entity.UserAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserAuditLogRepository extends AppendOnlyAuditRepository<UserAuditLog> {
    Page<UserAuditLog> findByTargetUserId(Long targetUserId, Pageable pageable);
}
