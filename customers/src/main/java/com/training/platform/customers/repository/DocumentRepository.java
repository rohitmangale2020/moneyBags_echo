package com.training.platform.customers.repository;

import com.training.platform.customers.entity.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<DocumentEntity, Long> {
    List<DocumentEntity> findByCustomerCustomerId(Long customerId);
    Optional<DocumentEntity> findByDocIdAndCustomerCustomerId(Long docId, Long customerId);
}