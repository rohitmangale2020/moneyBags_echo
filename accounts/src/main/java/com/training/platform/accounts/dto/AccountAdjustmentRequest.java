package com.training.platform.accounts.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Internal request used by the transactions service for a single-account posting. */
public record AccountAdjustmentRequest(
        @NotBlank @Size(max = 40) String transactionRef,
        @NotNull AdjustmentType adjustmentType,
        @NotNull @DecimalMin(value = "0.0001") BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currencyCode) {

    public enum AdjustmentType {
        DEPOSIT,
        WITHDRAWAL
    }
}
