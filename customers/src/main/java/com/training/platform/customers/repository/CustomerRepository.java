package com.training.platform.customers.repository;

import com.training.platform.customers.constants.CustomerStatus;
import com.training.platform.customers.entity.CustomerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<CustomerEntity, Long> {

    Optional<CustomerEntity> findByCifNo(String cifNo);

    Optional<CustomerEntity> findByEmail(String email);

    Optional<CustomerEntity> findByPhone(String phone);

    boolean existsByCifNo(String cifNo);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    List<CustomerEntity> findByStatus(CustomerStatus status);
}