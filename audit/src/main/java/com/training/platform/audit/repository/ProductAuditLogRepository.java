package com.training.platform.audit.repository;

import com.training.platform.audit.entity.ProductAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductAuditLogRepository extends AppendOnlyAuditRepository<ProductAuditLog> {
    Page<ProductAuditLog> findByProductId(Long productId, Pageable pageable);
}
