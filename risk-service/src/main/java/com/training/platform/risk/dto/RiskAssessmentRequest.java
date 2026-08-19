package com.training.platform.risk.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Data available before money is posted; completed balances and outcomes are intentionally absent. */
public record RiskAssessmentRequest(
        @NotBlank String transactionRef,
        @NotBlank String transactionType,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currencyCode,
        String debitAccountId,
        String creditAccountId,
        String externalBeneficiary,
        String initiatedByCustomerId,
        @NotNull @DecimalMin("0.00") BigDecimal oldBalanceOrg,
        @NotNull @DecimalMin("0.00") BigDecimal oldBalanceDest,
        LocalDateTime occurredAt) { }
