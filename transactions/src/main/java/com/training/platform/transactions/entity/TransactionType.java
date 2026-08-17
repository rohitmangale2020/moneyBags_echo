package com.training.platform.transactions.entity;

/** Defines the supported kinds of monetary transaction. */
public enum TransactionType {
    TRANSFER,
    DEPOSIT,
    OPENING_DEPOSIT,
    FIXED_DEPOSIT_FUNDING,
    WITHDRAWAL,
    PAYMENT,
    REVERSAL
}
