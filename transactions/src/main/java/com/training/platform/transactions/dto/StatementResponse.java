package com.training.platform.transactions.dto;

import com.training.platform.transactions.entity.AccountStatement;
import com.training.platform.transactions.entity.StatementEntryType;
import com.training.platform.transactions.entity.TransactionChannel;
import com.training.platform.transactions.entity.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StatementResponse(String statementId, String transactionId, String transactionRef,
                                TransactionType transactionType, TransactionChannel channel, String accountId,
                                String description, BigDecimal withdrawalAmount, BigDecimal depositAmount,
                                String currencyCode, BigDecimal closingBalance, LocalDateTime postedAt,
                                StatementEntryType entryType, BigDecimal amount, BigDecimal balanceAfter) {
    public static StatementResponse from(AccountStatement statement) {
        return new StatementResponse(statement.getStatementId(), statement.getTransaction().getTransactionId(),
                statement.getTransaction().getTransactionRef(), statement.getTransaction().getTransactionType(),
                TransactionChannel.from(statement.getTransaction()), statement.getAccountId(), statement.getDescription(),
                statement.getWithdrawalAmount(), statement.getDepositAmount(), statement.getCurrencyCode(),
                statement.getClosingBalance(), statement.getPostedAt(), statement.getEntryType(),
                statement.getAmount(), statement.getBalanceAfter());
    }
}
