package com.training.platform.audit.service;

import com.training.platform.audit.entity.ApiAccessAuditLog;
import com.training.platform.audit.repository.ApiAccessAuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApiAccessAuditLogService extends AppendOnlyAuditService<ApiAccessAuditLog> {
    private final ApiAccessAuditLogRepository repository;

    public ApiAccessAuditLogService(ApiAccessAuditLogRepository repository) {
        super(repository);
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<ApiAccessAuditLog> findByTargetService(String targetService, Pageable pageable) {
        return repository.findByTargetService(targetService, pageable);
    }

    @Transactional(readOnly = true)
    public Page<ApiAccessAuditLog> findByHttpStatus(Integer httpStatus, Pageable pageable) {
        return repository.findByHttpStatus(httpStatus, pageable);
    }
}
