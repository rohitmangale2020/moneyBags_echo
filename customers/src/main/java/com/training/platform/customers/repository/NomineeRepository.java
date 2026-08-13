package com.training.platform.customers.repository;

import com.training.platform.customers.entity.NomineeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NomineeRepository extends JpaRepository<NomineeEntity, Long> {

    List<NomineeEntity> findByCustomerCustomerId(Long customerId);

    Optional<NomineeEntity> findByNomineeIdAndCustomerCustomerId(Long nomineeId, Long customerId);

    boolean existsByCustomerCustomerIdAndStatusIgnoreCase(Long customerId, String status);

    boolean existsByCustomerCustomerIdAndNomineeNameIgnoreCaseAndRelationTypeIgnoreCaseAndStatusIgnoreCase(
            Long customerId,
            String nomineeName,
            String relationType,
            String status
    );
}
