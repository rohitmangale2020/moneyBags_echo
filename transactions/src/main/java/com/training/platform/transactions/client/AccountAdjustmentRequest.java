package com.training.platform.transactions.client;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AccountAdjustmentRequest(
        String transactionRef,
        AdjustmentType adjustmentType,
        BigDecimal amount,
        String currencyCode,
        LocalDate effectiveDate) {

    public AccountAdjustmentRequest(String transactionRef, AdjustmentType adjustmentType,
                                    BigDecimal amount, String currencyCode) {
        this(transactionRef, adjustmentType, amount, currencyCode, null);
    }

    public enum AdjustmentType {
        OPENING_DEPOSIT,
        DEPOSIT,
        WITHDRAWAL,
        MONTHLY_MAINTENANCE_FEE,
        ANNUAL_MAINTENANCE_FEE,
        INTEREST_CREDIT,
        FIXED_DEPOSIT_INTEREST_CREDIT
    }
}
