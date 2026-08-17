package com.training.platform.accounts.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Internal request used by the transactions service for a single-account posting. */
public record AccountAdjustmentRequest(
        @NotBlank @Size(max = 40) String transactionRef,
        @NotNull AdjustmentType adjustmentType,
        @NotNull @DecimalMin(value = "0.0001") BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currencyCode,
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
