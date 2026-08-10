package com.training.platform.customers.repository;

import com.training.platform.customers.entity.KycEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface KycRepository extends JpaRepository<KycEntity, Long> {

    Optional<KycEntity> findByCustomerCustomerId(Long customerId);

    boolean existsByCustomerCustomerId(Long customerId);
}