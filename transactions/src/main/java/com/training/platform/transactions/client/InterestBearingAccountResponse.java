package com.training.platform.transactions.client;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InterestBearingAccountResponse(
        String accountId,
        String customerId,
        String productId,
        String productTypeCode,
        String currencyCode,
        BigDecimal balance,
        BigDecimal annualInterestRate,
        LocalDate accruedThrough,
        LocalDate payoutDueDate) { }
