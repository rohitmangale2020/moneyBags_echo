package com.training.platform.transactions.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.training.platform.transactions.entity.BankTransaction;
import com.training.platform.transactions.entity.TransactionStatus;
import com.training.platform.transactions.entity.TransactionType;
import com.training.platform.transactions.repository.BankTransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BankTransactionServiceBehaviorTest {
    @Mock private BankTransactionRepository transactionRepository;
    private BankTransactionService transactionService;

    @BeforeEach
    void setUp() {
        transactionService = new BankTransactionService(transactionRepository);
    }

    @Test
    void returnsTransactionsForDebitAndCreditAccounts() {
        BankTransaction debit = validTransaction("REF-D");
        BankTransaction credit = validTransaction("REF-C");
        when(transactionRepository.findByDebitAccountId("account-a")).thenReturn(List.of(debit));
        when(transactionRepository.findByCreditAccountId("account-b")).thenReturn(List.of(credit));

        assertEquals(List.of(debit), transactionService.getDebitAccountTransactions("account-a"));
        assertEquals(List.of(credit), transactionService.getCreditAccountTransactions("account-b"));
    }

    @Test
    void rejectsMissingAndInvalidRequiredValues() {
        BankTransaction missingType = validTransaction("REF-1");
        missingType.setTransactionType(null);
        BankTransaction missingStatus = validTransaction("REF-2");
        missingStatus.setTransactionStatus(null);
        BankTransaction blankReference = validTransaction(" ");
        BankTransaction zeroAmount = validTransaction("REF-3");
        zeroAmount.setAmount(BigDecimal.ZERO);
        BankTransaction blankCurrency = validTransaction("REF-4");
        blankCurrency.setCurrencyCode(" ");

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> transactionService.initiate(null)),
                () -> assertThrows(IllegalArgumentException.class, () -> transactionService.initiate(missingType)),
                () -> assertThrows(IllegalArgumentException.class, () -> transactionService.initiate(missingStatus)),
                () -> assertThrows(IllegalArgumentException.class, () -> transactionService.initiate(blankReference)),
                () -> assertThrows(IllegalArgumentException.class, () -> transactionService.initiate(zeroAmount)),
                () -> assertThrows(IllegalArgumentException.class, () -> transactionService.initiate(blankCurrency)));
        verify(transactionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateCopiesAllMutableFieldsOntoTheManagedEntity() {
        BankTransaction existing = validTransaction("OLD-REF");
        BankTransaction replacement = validTransaction("NEW-REF");
        replacement.setTransactionType(TransactionType.PAYMENT);
        replacement.setTransactionStatus(TransactionStatus.COMPLETED);
        replacement.setDebitAccountId("new-debit");
        replacement.setCreditAccountId("new-credit");
        replacement.setExternalBeneficiary("Merchant");
        replacement.setAmount(new BigDecimal("125.50"));
        replacement.setCurrencyCode("USD");
        replacement.setFeeAmount(new BigDecimal("1.25"));
        replacement.setInitiatedByCustomerId("customer-2");
        replacement.setInitiatedByUserId("user-2");
        LocalDateTime completedAt = LocalDateTime.of(2025, 3, 4, 5, 6);
        replacement.setCompletedAt(completedAt);
        replacement.setFailureCode("NONE");
        replacement.setFailureReason("No failure");
        when(transactionRepository.findById("txn-1")).thenReturn(Optional.of(existing));
        when(transactionRepository.save(existing)).thenReturn(existing);

        BankTransaction updated = transactionService.update("txn-1", replacement);

        assertSame(existing, updated);
        assertAll(
                () -> assertEquals("NEW-REF", existing.getTransactionRef()),
                () -> assertEquals(TransactionType.PAYMENT, existing.getTransactionType()),
                () -> assertEquals(TransactionStatus.COMPLETED, existing.getTransactionStatus()),
                () -> assertEquals("new-debit", existing.getDebitAccountId()),
                () -> assertEquals("new-credit", existing.getCreditAccountId()),
                () -> assertEquals("Merchant", existing.getExternalBeneficiary()),
                () -> assertEquals(new BigDecimal("125.50"), existing.getAmount()),
                () -> assertEquals("USD", existing.getCurrencyCode()),
                () -> assertEquals(new BigDecimal("1.25"), existing.getFeeAmount()),
                () -> assertEquals("customer-2", existing.getInitiatedByCustomerId()),
                () -> assertEquals("user-2", existing.getInitiatedByUserId()),
                () -> assertEquals(completedAt, existing.getCompletedAt()),
                () -> assertEquals("NONE", existing.getFailureCode()),
                () -> assertEquals("No failure", existing.getFailureReason()));
        verify(transactionRepository).save(existing);
    }

    private static BankTransaction validTransaction(String reference) {
        BankTransaction transaction = new BankTransaction();
        transaction.setTransactionRef(reference);
        transaction.setTransactionType(TransactionType.TRANSFER);
        transaction.setTransactionStatus(TransactionStatus.INITIATED);
        transaction.setDebitAccountId("account-a");
        transaction.setCreditAccountId("account-b");
        transaction.setAmount(new BigDecimal("100.00"));
        transaction.setCurrencyCode("INR");
        transaction.setFeeAmount(BigDecimal.ZERO);
        return transaction;
    }
}
