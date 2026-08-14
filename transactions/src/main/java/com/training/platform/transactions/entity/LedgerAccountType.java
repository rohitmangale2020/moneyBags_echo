package com.training.platform.transactions.entity;

/** Normal balance convention used when maintaining a ledger account balance. */
public enum LedgerAccountType {
    ASSET,
    LIABILITY,
    INCOME,
    EXPENSE,
    EQUITY;

    public boolean hasDebitNormalBalance() {
        return this == ASSET || this == EXPENSE;
    }
}
