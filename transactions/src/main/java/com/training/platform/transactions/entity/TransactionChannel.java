package com.training.platform.transactions.entity;

/** Customer-facing transaction categories used by statements and filtering. */
public enum TransactionChannel {
    INTERNAL_TRANSFER,
    SELF_TRANSFER,
    DEPOSIT,
    OPENING_DEPOSIT,
    FIXED_DEPOSIT_FUNDING,
    WITHDRAWAL;

    public static TransactionChannel from(BankTransaction transaction) {
        if (transaction == null || transaction.getTransactionType() == null) return null;
        return switch (transaction.getTransactionType()) {
            case TRANSFER -> transaction.getInitiatedByCustomerId() == null
                    || transaction.getInitiatedByCustomerId().isBlank()
                    ? INTERNAL_TRANSFER : SELF_TRANSFER;
            case DEPOSIT -> DEPOSIT;
            case OPENING_DEPOSIT -> OPENING_DEPOSIT;
            case FIXED_DEPOSIT_FUNDING -> FIXED_DEPOSIT_FUNDING;
            case WITHDRAWAL -> WITHDRAWAL;
            default -> null;
        };
    }
}
