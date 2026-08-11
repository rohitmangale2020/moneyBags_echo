package com.training.platform.transactions.client;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AccountTransferResponse(
        String transactionRef,
        String debitAccountId,
        String creditAccountId,
        BigDecimal debitBalanceAfter,
        BigDecimal creditBalanceAfter,
        LocalDateTime processedAt) { }
