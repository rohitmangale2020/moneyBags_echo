package com.training.platform.transactions.repository;

import com.training.platform.transactions.entity.LedgerEntry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, String> {
    boolean existsByTransactionRef(String transactionRef);
    List<LedgerEntry> findByTransactionRefOrderByLineNumber(String transactionRef);
    List<LedgerEntry> findByLedgerAccountCodeOrderByPostingDateDescLineNumberDesc(String ledgerAccountCode);
}
