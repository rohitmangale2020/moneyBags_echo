package com.training.platform.audit.service;

import com.training.platform.audit.entity.CustomerAuditLog;
import com.training.platform.audit.repository.CustomerAuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerAuditLogService extends AppendOnlyAuditService<CustomerAuditLog> {
    private final CustomerAuditLogRepository repository;

    public CustomerAuditLogService(CustomerAuditLogRepository repository) {
        super(repository);
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<CustomerAuditLog> findByCustomerId(Long customerId, Pageable pageable) {
        return repository.findByCustomerId(customerId, pageable);
    }
}
