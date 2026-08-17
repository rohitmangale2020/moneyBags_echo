package com.training.platform.transactions.client;

import java.math.BigDecimal;

public record AccountTransferRequest(
        String transactionRef,
        String debitAccountId,
        String creditAccountId,
        BigDecimal amount,
        String currencyCode,
        String customerId,
        TransferPurpose purpose) {

    public AccountTransferRequest(String transactionRef, String debitAccountId, String creditAccountId,
                                  BigDecimal amount, String currencyCode, String customerId) {
        this(transactionRef, debitAccountId, creditAccountId, amount, currencyCode,
                customerId, TransferPurpose.STANDARD);
    }

    public enum TransferPurpose {
        STANDARD,
        FIXED_DEPOSIT_FUNDING,
        FIXED_DEPOSIT_MATURITY,
        FIXED_DEPOSIT_PREMATURE_CLOSURE
    }
}
