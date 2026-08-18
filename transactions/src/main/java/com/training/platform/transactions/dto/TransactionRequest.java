package com.training.platform.transactions.dto;

import com.training.platform.transactions.entity.TransactionStatus;
import com.training.platform.transactions.entity.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionRequest(
        @NotBlank @Size(max = 40) String transactionRef,
        @NotNull TransactionType transactionType,
        // Lifecycle state is controlled by the transaction service, never the caller.
        TransactionStatus transactionStatus,
        String debitAccountId, String creditAccountId, String externalBeneficiary,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currencyCode,
        @DecimalMin("0.00") BigDecimal feeAmount,
        String initiatedByCustomerId, String initiatedByUserId,
        LocalDateTime completedAt, String failureCode, String failureReason) { }
