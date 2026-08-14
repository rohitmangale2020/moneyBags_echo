package com.training.platform.audit.service;

import com.training.platform.audit.entity.ProductAuditLog;
import com.training.platform.audit.repository.ProductAuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductAuditLogService extends AppendOnlyAuditService<ProductAuditLog> {
    private final ProductAuditLogRepository repository;

    public ProductAuditLogService(ProductAuditLogRepository repository) {
        super(repository);
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<ProductAuditLog> findByProductId(Long productId, Pageable pageable) {
        return repository.findByProductId(productId, pageable);
    }
}
