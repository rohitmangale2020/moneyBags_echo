package com.training.platform.accounts.repository;

import com.training.platform.accounts.entity.AccountHolder;
import com.training.platform.accounts.entity.AccountHolderId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountHolderRepository extends JpaRepository<AccountHolder, AccountHolderId> {
    List<AccountHolder> findByIdCustomerId(String customerId);
    List<AccountHolder> findByAccountAccountId(String accountId);
}
