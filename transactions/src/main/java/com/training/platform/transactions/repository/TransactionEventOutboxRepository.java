package com.training.platform.transactions.repository;

import com.training.platform.transactions.entity.TransactionEventOutbox;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionEventOutboxRepository extends JpaRepository<TransactionEventOutbox, String> {
    List<TransactionEventOutbox> findByPublishedAtIsNullOrderByOccurredAtAsc();
}
