package com.training.platform.customers.repository;




import com.training.platform.customers.entity.Customer;
import com.training.platform.customers.entity.CustomerStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByCifNo(String cifNo);

    Optional<Customer> findByEmail(String email);

    Optional<Customer> findByPhone(String phone);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    boolean existsByCifNo(String cifNo);

    List<Customer> findByStatus(CustomerStatus status);
}