package com.training.platform.audit.service;

import com.training.platform.audit.entity.SecurityAuditLog;
import com.training.platform.audit.repository.SecurityAuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SecurityAuditLogService extends AppendOnlyAuditService<SecurityAuditLog> {
    private final SecurityAuditLogRepository repository;

    public SecurityAuditLogService(SecurityAuditLogRepository repository) {
        super(repository);
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<SecurityAuditLog> findByUserId(Long userId, Pageable pageable) {
        return repository.findByUserId(userId, pageable);
    }
}
