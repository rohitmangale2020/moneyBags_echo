package com.training.platform.accounts.client;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Product configuration copied onto an account when it is opened. */
public record ProductRulesResponse(
        Long productId,
        String productCode,
        String productName,
        String productTypeCode,
        String productTypeName,
        String productTypeDescription,
        String description,
        BigDecimal minimumBalance,
        BigDecimal maximumBalance,
        String currency,
        String status,
        Long versionNo,
        LocalDateTime createdDate,
        LocalDateTime updatedDate,
        String createdBy,
        String updatedBy,
        Rate rate,
        Term term,
        Fee fee) {

    public record Rate(BigDecimal interestRate) { }

    public record Term(Integer tenureMonths, BigDecimal installmentAmount,
                       String installmentFrequency, Integer lockInPeriod,
                       String maturityInstruction, Boolean prematureWithdrawalAllowed) { }

    public record Fee(BigDecimal annualMaintenanceFee) { }

    public BigDecimal annualInterestRate() {
        return rate == null || rate.interestRate() == null ? BigDecimal.ZERO : rate.interestRate();
    }
}
