package com.training.platform.transactions.client;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Account and contractual product-rule snapshot used by deposit workflows. */
public record AccountDetailsResponse(
        String accountId,
        String accountNumber,
        String customerId,
        String productId,
        String productTypeCode,
        BigDecimal minimumBalance,
        BigDecimal maximumBalance,
        BigDecimal annualInterestRate,
        Integer tenureMonths,
        Integer lockInPeriodMonths,
        String maturityInstruction,
        Boolean prematureWithdrawalAllowed,
        LocalDate interestAccruedThrough,
        LocalDate nextInterestPayoutDate,
        String ownershipType,
        String status,
        String currencyCode,
        BigDecimal availableBalance,
        LocalDateTime openedAt,
        LocalDateTime closedAt,
        Long versionNo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) { }
