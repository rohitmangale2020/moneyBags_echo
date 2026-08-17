package com.training.platform.transactions.entity;

/** Defines the supported kinds of monetary transaction. */
public enum TransactionType {
    TRANSFER,
    DEPOSIT,
    /** Initial funding posted when an account is opened; treated as a credit in statements. */
    OPENING_DEPOSIT,
    WITHDRAWAL,
    PAYMENT,
    REVERSAL
}
