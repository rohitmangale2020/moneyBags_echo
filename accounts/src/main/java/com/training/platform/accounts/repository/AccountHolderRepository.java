package com.training.platform.accounts.repository;

import com.training.platform.accounts.entity.AccountHolder;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountHolderRepository extends JpaRepository<AccountHolder, String> {
    List<AccountHolder> findByCustomerId(String customerId);
    List<AccountHolder> findByAccountAccountId(String accountId);
}
