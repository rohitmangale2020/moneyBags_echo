package com.training.platform.accounts.dto;

import com.training.platform.accounts.entity.AccountStatus;
import com.training.platform.accounts.entity.OwnershipType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AccountRequest(
        @NotBlank @Size(max = 24) String accountNumber,
        @NotBlank String customerId,
        @NotBlank String productId,
        @NotNull OwnershipType ownershipType,
        @NotNull AccountStatus status,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currencyCode,
        @NotNull @DecimalMin("0.00") BigDecimal availableBalance,
        LocalDateTime closedAt) { }
