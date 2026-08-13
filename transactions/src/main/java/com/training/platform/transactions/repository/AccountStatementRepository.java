package com.training.platform.transactions.repository;

import com.training.platform.transactions.entity.AccountStatement;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AccountStatementRepository extends JpaRepository<AccountStatement, String>,
        JpaSpecificationExecutor<AccountStatement> {
    List<AccountStatement> findByAccountIdOrderByPostedAtDesc(String accountId);

    List<AccountStatement>
    findByAccountIdAndPostedAtGreaterThanEqualAndPostedAtLessThanOrderByPostedAtAsc(
            String accountId, LocalDateTime start, LocalDateTime end);
}
