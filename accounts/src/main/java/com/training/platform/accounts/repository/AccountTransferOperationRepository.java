package com.training.platform.accounts.repository;

import com.training.platform.accounts.entity.AccountTransferOperation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountTransferOperationRepository extends JpaRepository<AccountTransferOperation, String> {
    Optional<AccountTransferOperation> findByTransactionRef(String transactionRef);
}
