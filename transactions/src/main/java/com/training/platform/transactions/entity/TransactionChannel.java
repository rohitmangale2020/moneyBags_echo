package com.training.platform.transactions.entity;

/** Customer-facing transaction categories used by statements and filtering. */
public enum TransactionChannel {
    INTERNAL_TRANSFER,
    SELF_TRANSFER,
    DEPOSIT,
    WITHDRAWAL;

    public static TransactionChannel from(BankTransaction transaction) {
        if (transaction == null || transaction.getTransactionType() == null) return null;
        return switch (transaction.getTransactionType()) {
            case TRANSFER -> transaction.getInitiatedByCustomerId() == null
                    || transaction.getInitiatedByCustomerId().isBlank()
                    ? INTERNAL_TRANSFER : SELF_TRANSFER;
            case OPENING_DEPOSIT, DEPOSIT -> DEPOSIT;
            case WITHDRAWAL -> WITHDRAWAL;
            default -> null;
        };
    }
}
