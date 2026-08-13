package com.training.platform.transactions.dto;

import com.training.platform.transactions.entity.StatementEntryType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public record StatementRequest(
        @NotBlank String transactionId,
        @NotBlank String accountId,
        @NotNull StatementEntryType entryType,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currencyCode,
        @NotNull BigDecimal balanceAfter) { }
