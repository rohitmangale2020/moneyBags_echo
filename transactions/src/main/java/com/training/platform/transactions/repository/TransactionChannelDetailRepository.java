package com.training.platform.transactions.repository;

import com.training.platform.transactions.entity.TransactionChannelDetail;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionChannelDetailRepository extends JpaRepository<TransactionChannelDetail, String> {
    List<TransactionChannelDetail> findByTransactionTransactionId(String transactionId);
}
