package com.training.platform.accounts.repository;

import com.training.platform.accounts.entity.AccountStatusHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountStatusHistoryRepository extends JpaRepository<AccountStatusHistory, String> {
    List<AccountStatusHistory> findByAccountAccountIdOrderByChangedAtDesc(String accountId);
}
