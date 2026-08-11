package com.training.platform.transactions.entity;

/** Defines transaction-domain events stored in the outbox. */
public enum TransactionEventType {
    TRANSACTION_INITIATED,
    TRANSACTION_APPROVED,
    TRANSACTION_REJECTED,
    TRANSACTION_COMPLETED,
    TRANSACTION_FAILED,
    TRANSACTION_REVERSED
}
