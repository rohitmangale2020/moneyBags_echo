package com.training.platform.transactions.service;

import com.training.platform.auditclient.AuditClient;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;

import com.training.platform.transactions.entity.BankTransaction;
import com.training.platform.transactions.entity.TransactionStatus;
import com.training.platform.transactions.entity.TransactionType;
import com.training.platform.transactions.repository.BankTransactionRepository;
import com.training.platform.transactions.repository.AccountStatementRepository;
import com.training.platform.transactions.repository.TransactionEventOutboxRepository;
import com.training.platform.transactions.client.AccountsClient;
import com.training.platform.transactions.client.CustomersClient;
import com.training.platform.transactions.client.AccountTransferResponse;
import com.training.platform.transactions.entity.AccountStatement;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BankTransactionServiceBehaviorTest {
    @Mock private BankTransactionRepository transactionRepository;
    @Mock private AccountStatementRepository statementRepository;
    @Mock private TransactionEventOutboxRepository outboxRepository;
    @Mock private AccountsClient accountsClient;
    @Mock private CustomersClient customersClient;
    @Mock private AuditClient auditClient;
    @Mock private LedgerService ledgerService;
    private BankTransactionService transactionService;

    @BeforeEach
    void setUp() {
        transactionService = new BankTransactionService(transactionRepository, statementRepository,
                outboxRepository, accountsClient, customersClient, new ObjectMapper().findAndRegisterModules(),
                auditClient, ledgerService);
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
        BankTransaction blankReference = validTransaction(" ");
        BankTransaction zeroAmount = validTransaction("REF-3");
        zeroAmount.setAmount(BigDecimal.ZERO);
        BankTransaction blankCurrency = validTransaction("REF-4");
        blankCurrency.setCurrencyCode(" ");

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> transactionService.initiate(null)),
                () -> assertThrows(IllegalArgumentException.class, () -> transactionService.initiate(missingType)),
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

    @Test
    void completedTransferCreatesBankStyleStatementDescriptionsAndAmounts() {
        BankTransaction transfer = validTransaction("REF-100");
        when(auditClient.changes(anyMap(), anyMap())).thenReturn(java.util.Map.of(
                "changedFields", "transactionStatus",
                "oldValuesJson", "{}",
                "newValuesJson", "{}"));
        when(transactionRepository.findByTransactionRef("REF-100")).thenReturn(Optional.empty());
        when(transactionRepository.saveAndFlush(transfer)).thenReturn(transfer);
        when(transactionRepository.save(transfer)).thenReturn(transfer);
        when(accountsClient.transfer(any())).thenReturn(new AccountTransferResponse(
                "REF-100", "account-a", "account-b", "123456789012", "987654321098",
                "1", "2", new BigDecimal("900.00"), new BigDecimal("1100.00"),
                LocalDateTime.now()));
        when(customersClient.displayName("1")).thenReturn("Alice Sender");
        when(customersClient.displayName("2")).thenReturn("Bob Receiver");

        BankTransaction completed = transactionService.initiate(transfer);

        assertEquals(TransactionStatus.COMPLETED, completed.getTransactionStatus());
        assertEquals("INTERNAL TRANSFER FROM ALICE SENDER TO BOB RECEIVER", completed.getDescription());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<AccountStatement>> entries = ArgumentCaptor.forClass(Iterable.class);
        verify(statementRepository).saveAll(entries.capture());
        List<AccountStatement> saved = new java.util.ArrayList<>();
        entries.getValue().forEach(saved::add);
        assertAll(
                () -> assertEquals(new BigDecimal("100.00"), saved.get(0).getWithdrawalAmount()),
                () -> assertEquals(new BigDecimal("900.00"), saved.get(0).getClosingBalance()),
                () -> assertEquals("INTERNAL TRANSFER TO BOB RECEIVER A/C XX1098 | REF REF-100",
                        saved.get(0).getDescription()),
                () -> assertEquals(new BigDecimal("100.00"), saved.get(1).getDepositAmount()),
                () -> assertEquals("INTERNAL TRANSFER FROM ALICE SENDER A/C XX9012 | REF REF-100",
                        saved.get(1).getDescription()));
        verify(auditClient).success(eq("transactions"), eq("TRANSACTION_INITIATED"),
                eq("Transaction initiated"), anyMap());
        verify(auditClient).success(eq("transactions"), eq("TRANSACTION_COMPLETED"),
                startsWith("Transaction completed"), anyMap());
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
