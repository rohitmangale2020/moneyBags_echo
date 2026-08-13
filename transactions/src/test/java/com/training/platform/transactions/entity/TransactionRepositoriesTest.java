package com.training.platform.transactions.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.training.platform.transactions.repository.AccountStatementRepository;
import com.training.platform.transactions.repository.BankTransactionRepository;
import com.training.platform.transactions.repository.TransactionApprovalRepository;
import com.training.platform.transactions.repository.TransactionEventOutboxRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class TransactionRepositoriesTest {
    @Autowired private BankTransactionRepository transactionRepository;
    @Autowired private AccountStatementRepository statementRepository;
    @Autowired private TransactionApprovalRepository approvalRepository;
    @Autowired private TransactionEventOutboxRepository outboxRepository;

    @Test
    void bankTransactionQueriesFindReferenceDebitAndCreditAccounts() {
        String reference = uniqueReference("QUERY");
        BankTransaction transaction = transaction(reference, "account-a", "account-b");
        transactionRepository.saveAndFlush(transaction);

        assertEquals(transaction.getTransactionId(),
                transactionRepository.findByTransactionRef(reference).orElseThrow().getTransactionId());
        assertEquals(List.of(transaction), transactionRepository.findByDebitAccountId("account-a"));
        assertEquals(List.of(transaction), transactionRepository.findByCreditAccountId("account-b"));
        assertTrue(transactionRepository.findByTransactionRef("missing").isEmpty());
    }

    @Test
    void statementQueryReturnsNewestPostingFirst() {
        BankTransaction transaction = transactionRepository.saveAndFlush(
                transaction(uniqueReference("STATEMENT"), "account-a", "account-b"));
        AccountStatement older = statement(
                transaction, "account-a", LocalDateTime.of(2025, 1, 1, 10, 0));
        AccountStatement newer = statement(
                transaction, "account-a", LocalDateTime.of(2025, 1, 1, 11, 0));
        statementRepository.saveAllAndFlush(List.of(older, newer));

        List<AccountStatement> result =
                statementRepository.findByAccountIdOrderByPostedAtDesc("account-a");

        assertEquals(List.of(newer.getStatementId(), older.getStatementId()),
                result.stream().map(AccountStatement::getStatementId).toList());
    }

    @Test
    void approvalQueryReturnsOnlyApprovalsForRequestedTransaction() {
        BankTransaction first = transactionRepository.saveAndFlush(
                transaction(uniqueReference("APPROVAL-1"), "account-a", "account-b"));
        BankTransaction second = transactionRepository.saveAndFlush(
                transaction(uniqueReference("APPROVAL-2"), "account-c", "account-d"));
        TransactionApproval expected = approval(first, "account-a", "customer-1");
        approvalRepository.saveAndFlush(expected);
        approvalRepository.saveAndFlush(approval(second, "account-c", "customer-2"));

        List<TransactionApproval> result =
                approvalRepository.findByTransactionTransactionId(first.getTransactionId());

        assertEquals(1, result.size());
        assertEquals(ReflectionTestUtils.getField(expected, "approvalId"),
                ReflectionTestUtils.getField(result.get(0), "approvalId"));
    }

    @Test
    void outboxQueryReturnsOnlyUnpublishedEventsOldestFirst() {
        TransactionEventOutbox older = outbox(
                "aggregate-1", LocalDateTime.of(2025, 1, 1, 10, 0), null);
        TransactionEventOutbox newer = outbox(
                "aggregate-2", LocalDateTime.of(2025, 1, 1, 11, 0), null);
        TransactionEventOutbox published = outbox(
                "aggregate-3", LocalDateTime.of(2025, 1, 1, 9, 0),
                LocalDateTime.of(2025, 1, 1, 9, 5));
        outboxRepository.saveAllAndFlush(List.of(newer, published, older));

        List<TransactionEventOutbox> result =
                outboxRepository.findByPublishedAtIsNullOrderByOccurredAtAsc();

        assertEquals(List.of("aggregate-1", "aggregate-2"), result.stream()
                .map(event -> (String) ReflectionTestUtils.getField(event, "aggregateId"))
                .toList());
        assertFalse(result.contains(published));
    }

    private static BankTransaction transaction(String reference, String debitAccount, String creditAccount) {
        BankTransaction transaction = new BankTransaction();
        transaction.setTransactionRef(reference);
        transaction.setTransactionType(TransactionType.TRANSFER);
        transaction.setTransactionStatus(TransactionStatus.INITIATED);
        transaction.setDebitAccountId(debitAccount);
        transaction.setCreditAccountId(creditAccount);
        transaction.setAmount(new BigDecimal("100.00"));
        transaction.setCurrencyCode("INR");
        transaction.setFeeAmount(BigDecimal.ZERO);
        return transaction;
    }

    private static AccountStatement statement(
            BankTransaction transaction, String accountId, LocalDateTime postedAt) {
        AccountStatement statement = new AccountStatement();
        statement.setTransaction(transaction);
        statement.setAccountId(accountId);
        statement.setEntryType(StatementEntryType.DEBIT);
        statement.setAmount(new BigDecimal("100.00"));
        statement.setCurrencyCode("INR");
        statement.setBalanceAfter(new BigDecimal("900.00"));
        ReflectionTestUtils.setField(statement, "postedAt", postedAt);
        return statement;
    }

    private static TransactionApproval approval(
            BankTransaction transaction, String accountId, String customerId) {
        TransactionApproval approval = new TransactionApproval();
        ReflectionTestUtils.setField(approval, "transaction", transaction);
        ReflectionTestUtils.setField(approval, "accountHolderAccountId", accountId);
        ReflectionTestUtils.setField(approval, "accountHolderCustomerId", customerId);
        ReflectionTestUtils.setField(approval, "approvalStatus", ApprovalStatus.APPROVED);
        ReflectionTestUtils.setField(approval, "approvedAt", LocalDateTime.of(2025, 1, 1, 10, 0));
        return approval;
    }

    private static TransactionEventOutbox outbox(
            String aggregateId, LocalDateTime occurredAt, LocalDateTime publishedAt) {
        TransactionEventOutbox event = new TransactionEventOutbox();
        ReflectionTestUtils.setField(event, "aggregateId", aggregateId);
        ReflectionTestUtils.setField(event, "eventType", TransactionEventType.TRANSACTION_INITIATED);
        ReflectionTestUtils.setField(event, "payloadJson", "{\"transactionId\":\"" + aggregateId + "\"}");
        ReflectionTestUtils.setField(event, "occurredAt", occurredAt);
        ReflectionTestUtils.setField(event, "publishedAt", publishedAt);
        return event;
    }

    private static String uniqueReference(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
