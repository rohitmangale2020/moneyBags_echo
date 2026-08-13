package com.training.platform.transactions.client;

import java.math.BigDecimal;

public record AccountAdjustmentRequest(
        String transactionRef,
        AdjustmentType adjustmentType,
        BigDecimal amount,
        String currencyCode) {

    public enum AdjustmentType {
        DEPOSIT,
        WITHDRAWAL
    }
}
