package com.training.platform.accounts.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Internal request used by the transactions service to post one atomic transfer. */
public record AccountTransferRequest(
        @NotBlank @Size(max = 40) String transactionRef,
        @NotBlank @Size(max = 36) String debitAccountId,
        @NotBlank @Size(max = 36) String creditAccountId,
        @NotNull @DecimalMin(value = "0.0001") BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currencyCode) { }
