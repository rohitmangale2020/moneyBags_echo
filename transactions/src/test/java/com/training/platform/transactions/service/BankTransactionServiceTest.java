package com.training.platform.transactions.service;

import com.training.platform.auditclient.AuditClient;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import com.training.platform.transactions.entity.BankTransaction;
import com.training.platform.transactions.entity.TransactionStatus;
import com.training.platform.transactions.entity.TransactionType;
import com.training.platform.transactions.repository.BankTransactionRepository;
import com.training.platform.transactions.repository.AccountStatementRepository;
import com.training.platform.transactions.repository.TransactionEventOutboxRepository;
import com.training.platform.transactions.repository.TransactionApprovalRepository;
import com.training.platform.transactions.client.AccountsClient;
import com.training.platform.transactions.client.CustomersClient;
import com.training.platform.transactions.client.RiskServiceClient;
import com.training.platform.transactions.client.AccountAdjustmentRequest;
import com.training.platform.transactions.client.AccountAdjustmentResponse;
import com.training.platform.transactions.entity.AccountStatement;
import com.training.platform.transactions.entity.StatementEntryType;
import com.training.platform.transactions.entity.TransactionEventOutbox;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class BankTransactionServiceTest {
    @Mock private BankTransactionRepository transactionRepository;
    @Mock private AccountStatementRepository statementRepository;
    @Mock private TransactionEventOutboxRepository outboxRepository;
    @Mock private AccountsClient accountsClient;
    @Mock private CustomersClient customersClient;
    @Mock private AuditClient auditClient;
    @Mock private LedgerService ledgerService;
    @Mock private RiskServiceClient riskServiceClient;
    @Mock private TransactionApprovalRepository approvalRepository;
    @Mock private BankTransaction transaction;
    private BankTransactionService transactionService;

    @BeforeEach void setUp() {
        transactionService = new BankTransactionService(transactionRepository, statementRepository,
                outboxRepository, accountsClient, customersClient, new ObjectMapper().findAndRegisterModules(),
                auditClient, ledgerService, riskServiceClient, approvalRepository);
        org.mockito.Mockito.lenient().when(auditClient.changes(any(), any())).thenReturn(Map.of(
                "changedFields", "transactionStatus,description",
                "oldValuesJson", "{}",
                "newValuesJson", "{}"));
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

    @Test void openingDepositCreditsTheNewAccountAndUsesReturnedBalanceForStatement() {
        BankTransaction openingDeposit = new BankTransaction();
        openingDeposit.setTransactionRef("OPEN-account-1");
        openingDeposit.setTransactionType(TransactionType.OPENING_DEPOSIT);
        openingDeposit.setTransactionStatus(TransactionStatus.INITIATED);
        openingDeposit.setCreditAccountId("account-1");
        openingDeposit.setAmount(new BigDecimal("1000.00"));
        openingDeposit.setFeeAmount(BigDecimal.ZERO);
        openingDeposit.setCurrencyCode("INR");
        openingDeposit.setInitiatedByCustomerId("customer-1");
        openingDeposit.setInitiatedByUserId("employee-1");
        when(transactionRepository.findByTransactionRef("OPEN-account-1")).thenReturn(Optional.empty());
        when(transactionRepository.saveAndFlush(openingDeposit)).thenReturn(openingDeposit);
        when(transactionRepository.save(openingDeposit)).thenReturn(openingDeposit);
        when(accountsClient.adjust(eq("account-1"), any())).thenReturn(new AccountAdjustmentResponse(
                "OPEN-account-1", "account-1", "123456789012", "customer-1",
                AccountAdjustmentRequest.AdjustmentType.OPENING_DEPOSIT,
                new BigDecimal("1000.00"), null));
        when(customersClient.displayName("customer-1")).thenReturn("Test Customer");
        when(statementRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BankTransaction completed = transactionService.initiate(openingDeposit);

        assertSame(TransactionStatus.COMPLETED, completed.getTransactionStatus());
        ArgumentCaptor<AccountAdjustmentRequest> adjustment =
                ArgumentCaptor.forClass(AccountAdjustmentRequest.class);
        verify(accountsClient).adjust(eq("account-1"), adjustment.capture());
        assertSame(AccountAdjustmentRequest.AdjustmentType.OPENING_DEPOSIT,
                adjustment.getValue().adjustmentType());
        assertEqualsMoney("1000.00", adjustment.getValue().amount());
        ArgumentCaptor<AccountStatement> statement = ArgumentCaptor.forClass(AccountStatement.class);
        verify(statementRepository).save(statement.capture());
        assertSame(StatementEntryType.CREDIT, statement.getValue().getEntryType());
        assertEqualsMoney("1000.00", statement.getValue().getDepositAmount());
        assertEqualsMoney("1000.00", statement.getValue().getBalanceAfter());
        verifyNoInteractions(riskServiceClient);
        verify(ledgerService).postCompletedTransaction(openingDeposit);
    }

    @Test void previouslyHeldOpeningDepositPostsAfterApprovalWithoutCallingRiskAgain() {
        BankTransaction openingDeposit = new BankTransaction();
        openingDeposit.setTransactionRef("OPEN-account-1");
        openingDeposit.setTransactionType(TransactionType.OPENING_DEPOSIT);
        openingDeposit.setTransactionStatus(TransactionStatus.PENDING_APPROVAL);
        openingDeposit.setCreditAccountId("account-1");
        openingDeposit.setAmount(new BigDecimal("1000.00"));
        openingDeposit.setFeeAmount(BigDecimal.ZERO);
        openingDeposit.setCurrencyCode("INR");
        openingDeposit.setInitiatedByCustomerId("customer-1");

        when(transactionRepository.findById("transaction-opening-1")).thenReturn(Optional.of(openingDeposit));
        when(transactionRepository.save(openingDeposit)).thenReturn(openingDeposit);
        when(accountsClient.adjust(eq("account-1"), any())).thenReturn(new AccountAdjustmentResponse(
                "OPEN-account-1", "account-1", "123456789012", "customer-1",
                AccountAdjustmentRequest.AdjustmentType.OPENING_DEPOSIT,
                new BigDecimal("1000.00"), null));
        when(customersClient.displayName("customer-1")).thenReturn("Test Customer");
        when(statementRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BankTransaction completed = transactionService.decidePendingApproval(
                "transaction-opening-1", true, "Release opening deposit", "admin-1");

        assertSame(TransactionStatus.COMPLETED, completed.getTransactionStatus());
        verify(accountsClient).adjust(eq("account-1"), any());
        verifyNoInteractions(riskServiceClient);
    }

    @Test void annualFeeDebitsAccountAndCreatesStatementLedgerAndOutboxRecords() {
        BankTransaction fee = new BankTransaction();
        fee.setTransactionRef("AF2026-account1");
        fee.setTransactionType(TransactionType.ANNUAL_MAINTENANCE_FEE);
        fee.setTransactionStatus(TransactionStatus.INITIATED);
        fee.setDebitAccountId("account-1");
        fee.setAmount(new BigDecimal("250.00"));
        fee.setFeeAmount(new BigDecimal("250.00"));
        fee.setCurrencyCode("INR");
        fee.setInitiatedByCustomerId("customer-1");
        fee.setInitiatedByUserId("SYSTEM");
        when(transactionRepository.findByTransactionRef("AF2026-account1")).thenReturn(Optional.empty());
        when(transactionRepository.saveAndFlush(fee)).thenReturn(fee);
        when(transactionRepository.save(fee)).thenReturn(fee);
        when(accountsClient.adjust(eq("account-1"), any())).thenReturn(new AccountAdjustmentResponse(
                "AF2026-account1", "account-1", "123456789012", "customer-1",
                AccountAdjustmentRequest.AdjustmentType.ANNUAL_MAINTENANCE_FEE,
                new BigDecimal("5750.00"), null));
        when(customersClient.displayName("customer-1")).thenReturn("Test Customer");
        when(statementRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BankTransaction completed = transactionService.initiate(fee);

        assertSame(TransactionStatus.COMPLETED, completed.getTransactionStatus());
        ArgumentCaptor<AccountAdjustmentRequest> adjustment =
                ArgumentCaptor.forClass(AccountAdjustmentRequest.class);
        verify(accountsClient).adjust(eq("account-1"), adjustment.capture());
        assertSame(AccountAdjustmentRequest.AdjustmentType.ANNUAL_MAINTENANCE_FEE,
                adjustment.getValue().adjustmentType());
        ArgumentCaptor<AccountStatement> statement = ArgumentCaptor.forClass(AccountStatement.class);
        verify(statementRepository).save(statement.capture());
        assertSame(StatementEntryType.DEBIT, statement.getValue().getEntryType());
        assertEqualsMoney("250.00", statement.getValue().getWithdrawalAmount());
        assertEqualsMoney("5750.00", statement.getValue().getBalanceAfter());
        org.junit.jupiter.api.Assertions.assertEquals(
                "Annual maintenance fee charged to TEST CUSTOMER for the 2026 account anniversary",
                completed.getDescription());
        org.junit.jupiter.api.Assertions.assertEquals(
                "Annual maintenance fee charged to TEST CUSTOMER for the 2026 account anniversary"
                        + " | REF AF2026-account1",
                statement.getValue().getDescription());
        verify(ledgerService).postCompletedTransaction(fee);
        ArgumentCaptor<TransactionEventOutbox> outbox = ArgumentCaptor.forClass(TransactionEventOutbox.class);
        verify(outboxRepository).save(outbox.capture());
        org.junit.jupiter.api.Assertions.assertTrue(outbox.getValue().getPayloadJson()
                .contains("\"maker\":\"SYSTEM\""));
        org.junit.jupiter.api.Assertions.assertTrue(outbox.getValue().getPayloadJson()
                .contains("Annual maintenance fee charged to TEST CUSTOMER"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> auditDetails = ArgumentCaptor.forClass(Map.class);
        verify(auditClient).success(eq("transactions"), eq("TRANSACTION_COMPLETED"),
                eq("Annual maintenance fee charged for the 2026 account anniversary"),
                auditDetails.capture());
        org.junit.jupiter.api.Assertions.assertEquals("SYSTEM", auditDetails.getValue().get("actorId"));
        org.junit.jupiter.api.Assertions.assertEquals("SYSTEM", auditDetails.getValue().get("actorType"));
    }

    @Test void savingsInterestUsesThePeriodEndInTransactionStatementAndAuditDescriptions() {
        BankTransaction interest = new BankTransaction();
        interest.setTransactionRef("SI2608-account1");
        interest.setTransactionType(TransactionType.INTEREST_CREDIT);
        interest.setTransactionStatus(TransactionStatus.INITIATED);
        interest.setCreditAccountId("account-1");
        interest.setAmount(new BigDecimal("40.00"));
        interest.setFeeAmount(BigDecimal.ZERO);
        interest.setCurrencyCode("INR");
        interest.setInitiatedByCustomerId("customer-1");
        interest.setInitiatedByUserId("SYSTEM");
        interest.setInterestPeriodEnd(LocalDate.of(2026, 8, 31));
        when(transactionRepository.findByTransactionRef("SI2608-account1")).thenReturn(Optional.empty());
        when(transactionRepository.saveAndFlush(interest)).thenReturn(interest);
        when(transactionRepository.save(interest)).thenReturn(interest);
        when(accountsClient.adjust(eq("account-1"), any())).thenReturn(new AccountAdjustmentResponse(
                "SI2608-account1", "account-1", "123456789012", "customer-1",
                AccountAdjustmentRequest.AdjustmentType.INTEREST_CREDIT,
                new BigDecimal("6040.00"), null));
        when(customersClient.displayName("customer-1")).thenReturn("Test Customer");
        when(statementRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BankTransaction completed = transactionService.initiate(interest);

        org.junit.jupiter.api.Assertions.assertEquals(
                "Savings interest credited to TEST CUSTOMER for period ending 2026-08-31",
                completed.getDescription());
        ArgumentCaptor<AccountStatement> statement = ArgumentCaptor.forClass(AccountStatement.class);
        verify(statementRepository).save(statement.capture());
        org.junit.jupiter.api.Assertions.assertEquals(
                "Savings interest credited to TEST CUSTOMER for period ending 2026-08-31"
                        + " | REF SI2608-account1",
                statement.getValue().getDescription());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> auditDetails = ArgumentCaptor.forClass(Map.class);
        verify(auditClient).success(eq("transactions"), eq("TRANSACTION_COMPLETED"),
                eq("Savings interest credited for period ending 2026-08-31"), auditDetails.capture());
        org.junit.jupiter.api.Assertions.assertEquals("SYSTEM", auditDetails.getValue().get("actorId"));
        org.junit.jupiter.api.Assertions.assertEquals(LocalDate.of(2026, 8, 31),
                auditDetails.getValue().get("businessDate"));
    }

    private static void assertEqualsMoney(String expected, BigDecimal actual) {
        org.junit.jupiter.api.Assertions.assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
