package com.training.platform.customers.repository;




import com.training.platform.customers.entity.CustomerEntity;
import com.training.platform.customers.constants.CustomerStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<CustomerEntity, Long> {

    Optional<CustomerEntity> findByCifNo(String cifNo);

    Optional<CustomerEntity> findByEmail(String email);

    Optional<CustomerEntity> findByPhone(String phone);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    boolean existsByCifNo(String cifNo);

    List<CustomerEntity> findByStatus(CustomerStatus status);
}