package com.training.platform.transactions.dto;

import com.training.platform.transactions.entity.BankTransaction;
import com.training.platform.transactions.entity.TransactionChannel;
import com.training.platform.transactions.entity.TransactionStatus;
import com.training.platform.transactions.entity.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse(
        String transactionId, String transactionRef, TransactionType transactionType,
        TransactionStatus transactionStatus, TransactionChannel transactionChannel,
        String debitAccountId, String creditAccountId, String externalBeneficiary,
        BigDecimal amount, String currencyCode, BigDecimal feeAmount,
        String initiatedByCustomerId, String initiatedByUserId, LocalDateTime initiatedAt,
        LocalDateTime completedAt, String failureCode, String failureReason) {
    public static TransactionResponse from(BankTransaction transaction) {
        return new TransactionResponse(transaction.getTransactionId(), transaction.getTransactionRef(), transaction.getTransactionType(),
                transaction.getTransactionStatus(), transaction.getTransactionChannel(), transaction.getDebitAccountId(),
                transaction.getCreditAccountId(), transaction.getExternalBeneficiary(), transaction.getAmount(), transaction.getCurrencyCode(),
                transaction.getFeeAmount(), transaction.getInitiatedByCustomerId(), transaction.getInitiatedByUserId(), transaction.getInitiatedAt(),
                transaction.getCompletedAt(), transaction.getFailureCode(), transaction.getFailureReason());
    }
}
