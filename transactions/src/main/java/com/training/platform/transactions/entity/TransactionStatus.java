package com.training.platform.transactions.entity;

/** Defines the processing lifecycle states of a transaction. */
public enum TransactionStatus {
    INITIATED,
    PENDING_APPROVAL,
    PROCESSING,
    COMPLETED,
    FAILED,
    REVERSED,
    CANCELLED
}
