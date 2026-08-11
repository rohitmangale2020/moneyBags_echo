package com.training.platform.customers.repository;

import com.training.platform.customers.entity.AddressEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AddressRepository extends JpaRepository<AddressEntity, Long> {

    List<AddressEntity> findByCustomerCustomerId(Long customerId);

    Optional<AddressEntity> findByAddressIdAndCustomerCustomerId(Long addressId, Long customerId);
}