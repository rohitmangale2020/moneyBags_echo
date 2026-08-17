package com.training.platform.transactions.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record FixedDepositOpenRequest(
        @NotBlank String fdAccountId,
        @NotBlank String fundingAccountId,
        @NotBlank String payoutAccountId,
        @NotNull @DecimalMin("0.01") BigDecimal principal) { }
