package com.training.platform.accounts.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Result of an atomic account-to-account balance transfer. */
public record AccountTransferResponse(
        String transactionRef,
        String debitAccountId,
        String creditAccountId,
        String debitAccountNumber,
        String creditAccountNumber,
        String debitCustomerId,
        String creditCustomerId,
        BigDecimal debitBalanceAfter,
        BigDecimal creditBalanceAfter,
        LocalDateTime processedAt) { }
