package com.training.platform.transactions.repository;

import com.training.platform.transactions.entity.LedgerAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, String> {
    Optional<LedgerAccount> findByCode(String code);
}
