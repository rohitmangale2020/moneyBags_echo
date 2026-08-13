package com.training.platform.accounts.repository;

import com.training.platform.accounts.entity.AccountBalanceOperation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountBalanceOperationRepository extends JpaRepository<AccountBalanceOperation, String> {
    Optional<AccountBalanceOperation> findByTransactionRef(String transactionRef);
}
