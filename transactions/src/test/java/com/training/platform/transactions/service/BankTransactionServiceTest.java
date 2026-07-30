package com.training.platform.transactions.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.training.platform.transactions.entity.BankTransaction;
import com.training.platform.transactions.repository.BankTransactionRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BankTransactionServiceTest {
    @Mock private BankTransactionRepository transactionRepository;
    @Mock private BankTransaction transaction;
    private BankTransactionService transactionService;

    @BeforeEach void setUp() { transactionService = new BankTransactionService(transactionRepository); }

    @Test void returnsTransactionWhenReferenceExists() {
        when(transactionRepository.findByTransactionRef("TXN-1")).thenReturn(Optional.of(transaction));
        assertSame(transaction, transactionService.getByReference("TXN-1"));
    }

    @Test void throwsWhenReferenceDoesNotExist() {
        when(transactionRepository.findByTransactionRef("missing")).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> transactionService.getByReference("missing"));
    }

    @Test void savesTransactionWhenInitiating() {
        when(transactionRepository.save(transaction)).thenReturn(transaction);
        assertSame(transaction, transactionService.initiate(transaction));
        verify(transactionRepository).save(transaction);
    }
}
