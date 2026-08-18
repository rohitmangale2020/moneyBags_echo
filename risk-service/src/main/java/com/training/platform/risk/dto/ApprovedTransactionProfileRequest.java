package com.training.platform.risk.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Sent only after a transaction has completed normally and is approved for profile learning. */
public record ApprovedTransactionProfileRequest(
        @NotBlank String customerId,
        String creditAccountId,
        String externalBeneficiary,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotNull LocalDateTime completedAt) { }
