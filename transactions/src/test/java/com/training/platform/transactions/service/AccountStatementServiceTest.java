package com.training.platform.transactions.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.training.platform.transactions.entity.AccountStatement;
import com.training.platform.transactions.entity.BankTransaction;
import com.training.platform.transactions.entity.StatementEntryType;
import com.training.platform.transactions.repository.AccountStatementRepository;
import com.training.platform.transactions.repository.BankTransactionRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountStatementServiceTest {
    @Mock private AccountStatementRepository statementRepository;
    @Mock private BankTransactionRepository transactionRepository;
    @Mock private AccountStatement statement;
    @Mock private BankTransaction transaction;
    private AccountStatementService statementService;

    @BeforeEach void setUp() { statementService = new AccountStatementService(statementRepository, transactionRepository); }

    @Test void returnsStatementWhenItExists() {
        when(statementRepository.findById("statement-1")).thenReturn(Optional.of(statement));
        assertSame(statement, statementService.getById("statement-1"));
    }

    @Test void throwsWhenStatementDoesNotExist() {
        when(statementRepository.findById("missing")).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> statementService.getById("missing"));
    }

    @Test void savesValidStatement() {
        when(transactionRepository.findById("transaction-1")).thenReturn(Optional.of(transaction));
        when(statement.getTransaction()).thenReturn(transaction);
        when(statement.getAccountId()).thenReturn("account-1");
        when(statement.getEntryType()).thenReturn(StatementEntryType.DEBIT);
        when(statement.getAmount()).thenReturn(BigDecimal.ONE);
        when(statement.getCurrencyCode()).thenReturn("INR");
        when(statement.getBalanceAfter()).thenReturn(BigDecimal.TEN);
        when(statementRepository.save(statement)).thenReturn(statement);

        assertSame(statement, statementService.record("transaction-1", statement));
        verify(statementRepository).save(statement);
    }
}
