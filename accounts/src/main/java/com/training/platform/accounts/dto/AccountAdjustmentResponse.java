package com.training.platform.accounts.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AccountAdjustmentResponse(
        String transactionRef,
        String accountId,
        AccountAdjustmentRequest.AdjustmentType adjustmentType,
        BigDecimal balanceAfter,
        LocalDateTime processedAt) { }
