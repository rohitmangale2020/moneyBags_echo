package com.training.platform.transactions.service;

import com.training.platform.auditclient.AuditClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.training.platform.transactions.entity.AccountStatement;
import com.training.platform.transactions.entity.BankTransaction;
import com.training.platform.transactions.entity.StatementEntryType;
import com.training.platform.transactions.repository.AccountStatementRepository;
import com.training.platform.transactions.repository.BankTransactionRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountStatementServiceBehaviorTest {
    @Mock private AccountStatementRepository statementRepository;
    @Mock private BankTransactionRepository transactionRepository;
    @Mock private AuditClient auditClient;
    private AccountStatementService statementService;

    @BeforeEach
    void setUp() {
        statementService = new AccountStatementService(statementRepository, transactionRepository, auditClient);
    }

    @Test
    void returnsAccountStatementsInRepositoryOrder() {
        List<AccountStatement> statements = List.of(new AccountStatement(), new AccountStatement());
        when(statementRepository.findByAccountIdOrderByPostedAtDesc("account-1")).thenReturn(statements);

        assertSame(statements, statementService.getByAccountId("account-1"));
    }

    @Test
    void recordAssociatesTheTransactionBeforeSaving() {
        BankTransaction transaction = new BankTransaction();
        AccountStatement statement = validStatement();
        when(transactionRepository.findById("txn-1")).thenReturn(Optional.of(transaction));
        when(statementRepository.save(statement)).thenReturn(statement);

        AccountStatement saved = statementService.record("txn-1", statement);

        assertSame(statement, saved);
        assertSame(transaction, statement.getTransaction());
        verify(statementRepository).save(statement);
    }

    @Test
    void recordRejectsAnUnknownTransaction() {
        AccountStatement statement = validStatement();
        when(transactionRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> statementService.record("missing", statement));
        verify(statementRepository, never()).save(statement);
    }

    @Test
    void recordRejectsInvalidStatementData() {
        BankTransaction transaction = new BankTransaction();
        AccountStatement statement = validStatement();
        statement.setAmount(BigDecimal.ZERO);
        when(transactionRepository.findById("txn-1")).thenReturn(Optional.of(transaction));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> statementService.record("txn-1", statement));

        assertEquals("Amount must be greater than zero", exception.getMessage());
        verify(statementRepository, never()).save(statement);
    }

    private static AccountStatement validStatement() {
        AccountStatement statement = new AccountStatement();
        statement.setAccountId("account-1");
        statement.setEntryType(StatementEntryType.DEBIT);
        statement.setAmount(new BigDecimal("50.00"));
        statement.setCurrencyCode("INR");
        statement.setBalanceAfter(new BigDecimal("950.00"));
        return statement;
    }
}
