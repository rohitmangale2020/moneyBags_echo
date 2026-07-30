package com.training.platform.transactions.repository;

import com.training.platform.transactions.entity.TransactionApproval;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionApprovalRepository extends JpaRepository<TransactionApproval, String> {
    List<TransactionApproval> findByTransactionTransactionId(String transactionId);
}
