package com.training.platform.transactions.repository;

import com.training.platform.transactions.entity.BankTransaction;
import com.training.platform.transactions.entity.TransactionStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, String> {
    List<BankTransaction> findAllByOrderByInitiatedAtDesc();
    Page<BankTransaction> findAllByOrderByInitiatedAtDesc(Pageable pageable);
    Optional<BankTransaction> findByTransactionRef(String transactionRef);
    List<BankTransaction> findByDebitAccountId(String debitAccountId);
    List<BankTransaction> findByCreditAccountId(String creditAccountId);
    List<BankTransaction> findByTransactionStatusOrderByInitiatedAtAsc(TransactionStatus transactionStatus);
}
