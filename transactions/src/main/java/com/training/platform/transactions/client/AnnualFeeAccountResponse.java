package com.training.platform.transactions.client;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AnnualFeeAccountResponse(
        String accountId,
        String customerId,
        String productId,
        String productTypeCode,
        String currencyCode,
        BigDecimal balance,
        LocalDateTime openedAt,
        BigDecimal annualMaintenanceFee) { }
