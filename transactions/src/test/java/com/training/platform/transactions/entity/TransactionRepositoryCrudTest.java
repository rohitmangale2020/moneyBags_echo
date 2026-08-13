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
class TransactionRepositoryCrudTest {
    @Autowired private BankTransactionRepository transactionRepository;
    @Autowired private AccountStatementRepository statementRepository;
    @Autowired private TransactionApprovalRepository approvalRepository;
    @Autowired private TransactionEventOutboxRepository outboxRepository;

    @Test
    void bankTransactionRepositorySupportsCreateReadUpdateAndDelete() {
        BankTransaction transaction = validTransaction(uniqueReference("CRUD-TXN"));

        BankTransaction created = transactionRepository.saveAndFlush(transaction);
        String id = created.getTransactionId();

        assertTrue(transactionRepository.findById(id).isPresent());
        assertEquals(TransactionStatus.INITIATED,
                transactionRepository.findById(id).orElseThrow().getTransactionStatus());

        created.setTransactionStatus(TransactionStatus.COMPLETED);
        created.setCompletedAt(LocalDateTime.of(2025, 1, 1, 12, 0));
        transactionRepository.saveAndFlush(created);

        BankTransaction updated = transactionRepository.findById(id).orElseThrow();
        assertEquals(TransactionStatus.COMPLETED, updated.getTransactionStatus());
        assertEquals(LocalDateTime.of(2025, 1, 1, 12, 0), updated.getCompletedAt());

        transactionRepository.deleteById(id);
        transactionRepository.flush();

        assertFalse(transactionRepository.existsById(id));
    }

    @Test
    void accountStatementRepositorySupportsCreateReadUpdateAndDelete() {
        BankTransaction transaction = transactionRepository.saveAndFlush(
                validTransaction(uniqueReference("CRUD-STMT")));
        AccountStatement statement = new AccountStatement();
        statement.setTransaction(transaction);
        statement.setAccountId("account-1");
        statement.setEntryType(StatementEntryType.DEBIT);
        statement.setAmount(new BigDecimal("100.00"));
        statement.setCurrencyCode("INR");
        statement.setBalanceAfter(new BigDecimal("900.00"));

        AccountStatement created = statementRepository.saveAndFlush(statement);
        String id = created.getStatementId();

        assertEquals(new BigDecimal("900.00"),
                statementRepository.findById(id).orElseThrow().getBalanceAfter());

        created.setBalanceAfter(new BigDecimal("850.00"));
        statementRepository.saveAndFlush(created);

        assertEquals(new BigDecimal("850.00"),
                statementRepository.findById(id).orElseThrow().getBalanceAfter());

        statementRepository.deleteById(id);
        statementRepository.flush();

        assertFalse(statementRepository.existsById(id));
    }

    @Test
    void transactionApprovalRepositorySupportsCreateReadUpdateAndDelete() {
        BankTransaction transaction = transactionRepository.saveAndFlush(
                validTransaction(uniqueReference("CRUD-APR")));
        TransactionApproval approval = new TransactionApproval();
        ReflectionTestUtils.setField(approval, "transaction", transaction);
        ReflectionTestUtils.setField(approval, "accountHolderAccountId", "account-1");
        ReflectionTestUtils.setField(approval, "accountHolderCustomerId", "customer-1");
        ReflectionTestUtils.setField(approval, "approvalStatus", ApprovalStatus.PENDING);

        TransactionApproval created = approvalRepository.saveAndFlush(approval);
        String id = (String) ReflectionTestUtils.getField(created, "approvalId");

        TransactionApproval read = approvalRepository.findById(id).orElseThrow();
        assertEquals(ApprovalStatus.PENDING,
                ReflectionTestUtils.getField(read, "approvalStatus"));

        ReflectionTestUtils.setField(created, "approvalStatus", ApprovalStatus.APPROVED);
        ReflectionTestUtils.setField(created, "approvedAt", LocalDateTime.of(2025, 1, 1, 12, 0));
        approvalRepository.saveAndFlush(created);

        TransactionApproval updated = approvalRepository.findById(id).orElseThrow();
        assertEquals(ApprovalStatus.APPROVED,
                ReflectionTestUtils.getField(updated, "approvalStatus"));

        approvalRepository.deleteById(id);
        approvalRepository.flush();

        assertFalse(approvalRepository.existsById(id));
    }

    @Test
    void transactionOutboxRepositorySupportsCreateReadUpdateAndDelete() {
        TransactionEventOutbox event = new TransactionEventOutbox();
        ReflectionTestUtils.setField(event, "aggregateId", "aggregate-1");
        ReflectionTestUtils.setField(event, "eventType", TransactionEventType.TRANSACTION_INITIATED);
        ReflectionTestUtils.setField(event, "payloadJson", "{\"transactionId\":\"aggregate-1\"}");

        TransactionEventOutbox created = outboxRepository.saveAndFlush(event);
        String id = (String) ReflectionTestUtils.getField(created, "eventId");

        TransactionEventOutbox read = outboxRepository.findById(id).orElseThrow();
        assertEquals(0, ReflectionTestUtils.getField(read, "retryCount"));
        assertEquals(null, ReflectionTestUtils.getField(read, "publishedAt"));

        LocalDateTime publishedAt = LocalDateTime.of(2025, 1, 1, 12, 0);
        ReflectionTestUtils.setField(created, "publishedAt", publishedAt);
        ReflectionTestUtils.setField(created, "retryCount", 1);
        outboxRepository.saveAndFlush(created);

        TransactionEventOutbox updated = outboxRepository.findById(id).orElseThrow();
        assertEquals(publishedAt, ReflectionTestUtils.getField(updated, "publishedAt"));
        assertEquals(1, ReflectionTestUtils.getField(updated, "retryCount"));

        outboxRepository.deleteById(id);
        outboxRepository.flush();

        assertFalse(outboxRepository.existsById(id));
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

    private static String uniqueReference(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
