package com.training.platform.transactions.client;

import java.math.BigDecimal;

public record AccountTransferRequest(
        String transactionRef,
        String debitAccountId,
        String creditAccountId,
        BigDecimal amount,
        String currencyCode,
        String customerId) { }
