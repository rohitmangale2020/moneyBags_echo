package com.training.platform.transactions.dto;

import com.training.platform.transactions.entity.AccountStatement;
import com.training.platform.transactions.entity.StatementEntryType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StatementResponse(String statementId, String transactionId, String accountId,
                                StatementEntryType entryType, BigDecimal amount, String currencyCode,
                                BigDecimal balanceAfter, LocalDateTime postedAt) {
    public static StatementResponse from(AccountStatement statement) {
        return new StatementResponse(statement.getStatementId(), statement.getTransaction().getTransactionId(), statement.getAccountId(),
                statement.getEntryType(), statement.getAmount(), statement.getCurrencyCode(), statement.getBalanceAfter(), statement.getPostedAt());
    }
}
