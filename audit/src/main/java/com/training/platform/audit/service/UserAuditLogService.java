package com.training.platform.audit.service;

import com.training.platform.audit.entity.UserAuditLog;
import com.training.platform.audit.repository.UserAuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAuditLogService extends AppendOnlyAuditService<UserAuditLog> {
    private final UserAuditLogRepository repository;

    public UserAuditLogService(UserAuditLogRepository repository) {
        super(repository);
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<UserAuditLog> findByTargetUserId(Long targetUserId, Pageable pageable) {
        return repository.findByTargetUserId(targetUserId, pageable);
    }
}
