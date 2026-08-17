package com.training.platform.transactions.dto;

import com.training.platform.transactions.entity.BankTransaction;
import com.training.platform.transactions.entity.TransactionStatus;
import com.training.platform.transactions.entity.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;

public record TransactionResponse(
        String transactionId, String transactionRef, TransactionType transactionType,
        TransactionStatus transactionStatus,
        String debitAccountId, String creditAccountId, String externalBeneficiary, String description,
        BigDecimal amount, String currencyCode, BigDecimal feeAmount,
        String initiatedByCustomerId, String initiatedByUserId, LocalDateTime initiatedAt,
        LocalDateTime completedAt, LocalDate interestPeriodEnd, String failureCode, String failureReason) {
    public static TransactionResponse from(BankTransaction transaction) {
        return new TransactionResponse(transaction.getTransactionId(), transaction.getTransactionRef(), transaction.getTransactionType(),
                transaction.getTransactionStatus(),transaction.getDebitAccountId(),
                transaction.getCreditAccountId(), transaction.getExternalBeneficiary(), transaction.getDescription(),
                transaction.getAmount(), transaction.getCurrencyCode(),
                transaction.getFeeAmount(), transaction.getInitiatedByCustomerId(), transaction.getInitiatedByUserId(), transaction.getInitiatedAt(),
                transaction.getCompletedAt(), transaction.getInterestPeriodEnd(), transaction.getFailureCode(), transaction.getFailureReason());
    }
}
