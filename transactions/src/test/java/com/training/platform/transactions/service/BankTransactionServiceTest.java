package com.training.platform.transactions.service;

import com.training.platform.auditclient.AuditClient;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.training.platform.transactions.entity.BankTransaction;
import com.training.platform.transactions.entity.TransactionStatus;
import com.training.platform.transactions.entity.TransactionType;
import com.training.platform.transactions.repository.BankTransactionRepository;
import com.training.platform.transactions.repository.AccountStatementRepository;
import com.training.platform.transactions.repository.TransactionEventOutboxRepository;
import com.training.platform.transactions.client.AccountsClient;
import com.training.platform.transactions.client.CustomersClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BankTransactionServiceTest {
    @Mock private BankTransactionRepository transactionRepository;
    @Mock private AccountStatementRepository statementRepository;
    @Mock private TransactionEventOutboxRepository outboxRepository;
    @Mock private AccountsClient accountsClient;
    @Mock private CustomersClient customersClient;
    @Mock private AuditClient auditClient;
    @Mock private BankTransaction transaction;
    private BankTransactionService transactionService;

    @BeforeEach void setUp() {
        transactionService = new BankTransactionService(transactionRepository, statementRepository,
                outboxRepository, accountsClient, customersClient, new ObjectMapper(), auditClient);
    }

    @Test void returnsTransactionWhenReferenceExists() {
        when(transactionRepository.findByTransactionRef("TXN-1")).thenReturn(Optional.of(transaction));
        assertSame(transaction, transactionService.getByReference("TXN-1"));
    }

    @Test void throwsWhenReferenceDoesNotExist() {
        when(transactionRepository.findByTransactionRef("missing")).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> transactionService.getByReference("missing"));
    }

    @Test void returnsAllTransactionsNewestFirstFromRepository() {
        List<BankTransaction> transactions = List.of(transaction);
        when(transactionRepository.findAllByOrderByInitiatedAtDesc()).thenReturn(transactions);

        assertSame(transactions, transactionService.getAllTransactions());
        verify(transactionRepository).findAllByOrderByInitiatedAtDesc();
    }

    @Test void rejectsTransferWithoutBothAccounts() {
        when(transaction.getTransactionType()).thenReturn(TransactionType.TRANSFER);
        when(transaction.getTransactionRef()).thenReturn("TXN-1");
        assertThrows(IllegalArgumentException.class, () -> transactionService.initiate(transaction));
    }
}
