package com.training.platform.transactions.repository;

import com.training.platform.transactions.entity.AccountStatement;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountStatementRepository extends JpaRepository<AccountStatement, String> {
    List<AccountStatement> findByAccountIdOrderByPostedAtDesc(String accountId);

    List<AccountStatement>
    findByAccountIdAndPostedAtGreaterThanEqualAndPostedAtLessThanOrderByPostedAtAsc(
            String accountId, LocalDateTime start, LocalDateTime end);
}
