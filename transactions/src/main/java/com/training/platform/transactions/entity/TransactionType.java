package com.training.platform.transactions.entity;

/** Defines the supported kinds of monetary transaction. */
public enum TransactionType {
    TRANSFER,
    OPENING_DEPOSIT,
    DEPOSIT,
    WITHDRAWAL,
    MONTHLY_MAINTENANCE_FEE,
    ANNUAL_MAINTENANCE_FEE,
    INTEREST_CREDIT,
    FIXED_DEPOSIT_INTEREST_CREDIT,
    FIXED_DEPOSIT_FUNDING,
    FIXED_DEPOSIT_MATURITY,
    FIXED_DEPOSIT_PREMATURE_CLOSURE,
    PAYMENT,
    REVERSAL
}
