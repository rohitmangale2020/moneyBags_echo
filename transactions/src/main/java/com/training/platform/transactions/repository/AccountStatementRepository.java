package com.training.platform.transactions.repository;

import com.training.platform.transactions.entity.AccountStatement;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountStatementRepository extends JpaRepository<AccountStatement, String> {
    List<AccountStatement> findByAccountIdOrderByPostedAtDesc(String accountId);
}
