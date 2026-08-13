package com.training.platform.transactions.dto;

import com.training.platform.transactions.entity.StatementEntryType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record StatementRequest(
        @NotBlank String transactionId,
        @NotBlank String accountId,
        @Size(max = 500) String description,
        @NotNull StatementEntryType entryType,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currencyCode,
        @NotNull BigDecimal balanceAfter) {
    public StatementRequest(String transactionId, String accountId, StatementEntryType entryType,
                            BigDecimal amount, String currencyCode, BigDecimal balanceAfter) {
        this(transactionId, accountId, null, entryType, amount, currencyCode, balanceAfter);
    }
}
