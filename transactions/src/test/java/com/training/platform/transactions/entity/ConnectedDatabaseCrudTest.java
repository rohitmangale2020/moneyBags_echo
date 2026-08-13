package com.training.platform.transactions.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.training.platform.transactions.repository.AccountStatementRepository;
import com.training.platform.transactions.repository.BankTransactionRepository;
import com.training.platform.transactions.repository.TransactionApprovalRepository;
import com.training.platform.transactions.repository.TransactionEventOutboxRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.annotation.Commit;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Oracle-connected CRUD tests with a separate committed transaction per operation.
 * Run methods individually to inspect the database between operations.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
@TestMethodOrder(OrderAnnotation.class)
@Commit
class ConnectedDatabaseCrudTest {
    private static final Logger log = LoggerFactory.getLogger(ConnectedDatabaseCrudTest.class);

    private static final String DEBIT_ACCOUNT_ID = "00000000-0000-0000-0000-000000000101";
    private static final String CREDIT_ACCOUNT_ID = "00000000-0000-0000-0000-000000000102";
    private static final String CUSTOMER_ID = "00000000-0000-0000-0000-000000000201";
    private static final String PRODUCT_ID = "00000000-0000-0000-0000-000000000301";
    private static final String DEBIT_ACCOUNT_NUMBER = "TST-CRUD-DBT";
    private static final String CREDIT_ACCOUNT_NUMBER = "TST-CRUD-CRT";
    private static final String TRANSACTION_REFERENCE = "DB-CRUD-VISIBLE-001";

    @Autowired private BankTransactionRepository transactionRepository;
    @Autowired private AccountStatementRepository statementRepository;
    @Autowired private TransactionApprovalRepository approvalRepository;
    @Autowired private TransactionEventOutboxRepository outboxRepository;
    @Autowired private EntityManager entityManager;

    @Test
    @Order(1)
    void createAccountsAndTransaction() {
        createAccountIfMissing(
                DEBIT_ACCOUNT_ID,
                DEBIT_ACCOUNT_NUMBER,
                CUSTOMER_ID,
                new BigDecimal("1000.00"));
        createAccountIfMissing(
                CREDIT_ACCOUNT_ID,
                CREDIT_ACCOUNT_NUMBER,
                "00000000-0000-0000-0000-000000000202",
                new BigDecimal("500.00"));

        assertTrue(
                transactionRepository.findByTransactionRef(TRANSACTION_REFERENCE).isEmpty(),
                "The visible transaction already exists; run deleteCreatedTransaction first");

        BankTransaction transaction = new BankTransaction();
        transaction.setTransactionRef(TRANSACTION_REFERENCE);
        transaction.setTransactionType(TransactionType.TRANSFER);
        transaction.setTransactionStatus(TransactionStatus.INITIATED);
        transaction.setDebitAccountId(DEBIT_ACCOUNT_ID);
        transaction.setCreditAccountId(CREDIT_ACCOUNT_ID);
        transaction.setAmount(new BigDecimal("100.00"));
        transaction.setCurrencyCode("INR");
        transaction.setFeeAmount(BigDecimal.ZERO);
        transaction.setInitiatedByCustomerId(CUSTOMER_ID);

        BankTransaction created = transactionRepository.saveAndFlush(transaction);

        assertTrue(transactionRepository.existsById(created.getTransactionId()));
        assertEquals(TransactionStatus.INITIATED, created.getTransactionStatus());
        log.info(
                "CREATE committed: transactionRef={}, transactionId={}, debitAccountId={}, creditAccountId={}",
                TRANSACTION_REFERENCE,
                created.getTransactionId(),
                DEBIT_ACCOUNT_ID,
                CREDIT_ACCOUNT_ID);
    }

    @Test
    @Order(2)
    void readCreatedTransaction() {
        BankTransaction transaction = requiredTransaction();

        assertEquals(TRANSACTION_REFERENCE, transaction.getTransactionRef());
        assertEquals(DEBIT_ACCOUNT_ID, transaction.getDebitAccountId());
        assertEquals(CREDIT_ACCOUNT_ID, transaction.getCreditAccountId());
        assertEquals(TransactionStatus.INITIATED, transaction.getTransactionStatus());
        assertEquals(1L, accountCount(DEBIT_ACCOUNT_ID));
        assertEquals(1L, accountCount(CREDIT_ACCOUNT_ID));

        log.info(
                "READ committed: transactionRef={}, status={}, amount={}",
                transaction.getTransactionRef(),
                transaction.getTransactionStatus(),
                transaction.getAmount());
    }

    @Test
    @Order(3)
    void updateCreatedTransaction() {
        BankTransaction transaction = requiredTransaction();
        assertEquals(
                TransactionStatus.INITIATED,
                transaction.getTransactionStatus(),
                "The transaction has already been updated");

        transaction.setTransactionStatus(TransactionStatus.COMPLETED);
        transaction.setCompletedAt(LocalDateTime.now());
        BankTransaction updated = transactionRepository.saveAndFlush(transaction);

        updateAccountBalance(DEBIT_ACCOUNT_ID, new BigDecimal("900.00"));
        updateAccountBalance(CREDIT_ACCOUNT_ID, new BigDecimal("600.00"));

        AccountStatement debitStatement = statement(
                updated, DEBIT_ACCOUNT_ID, StatementEntryType.DEBIT, new BigDecimal("900.00"));
        AccountStatement creditStatement = statement(
                updated, CREDIT_ACCOUNT_ID, StatementEntryType.CREDIT, new BigDecimal("600.00"));
        statementRepository.saveAllAndFlush(List.of(debitStatement, creditStatement));

        TransactionApproval approval = approval(updated);
        approvalRepository.saveAndFlush(approval);
        TransactionEventOutbox event = outboxEvent(updated.getTransactionId());
        outboxRepository.saveAndFlush(event);

        assertEquals(TransactionStatus.COMPLETED, updated.getTransactionStatus());
        assertMoneyEquals(new BigDecimal("900.00"), accountBalance(DEBIT_ACCOUNT_ID));
        assertMoneyEquals(new BigDecimal("600.00"), accountBalance(CREDIT_ACCOUNT_ID));
        log.info(
                "UPDATE committed: transactionRef={}, status={}, debitBalance={}, creditBalance={}",
                TRANSACTION_REFERENCE,
                updated.getTransactionStatus(),
                accountBalance(DEBIT_ACCOUNT_ID),
                accountBalance(CREDIT_ACCOUNT_ID));
    }

    @Test
    @Order(4)
    void deleteCreatedTransaction() {
        BankTransaction transaction = requiredTransaction();
        String transactionId = transaction.getTransactionId();

        entityManager.createNativeQuery(
                        "DELETE FROM account_statement WHERE transaction_id = :transactionId")
                .setParameter("transactionId", transactionId)
                .executeUpdate();
        entityManager.createNativeQuery(
                        "DELETE FROM transaction_approval WHERE transaction_id = :transactionId")
                .setParameter("transactionId", transactionId)
                .executeUpdate();
        entityManager.createNativeQuery(
                        "DELETE FROM transaction_event_outbox WHERE aggregate_id = :transactionId")
                .setParameter("transactionId", transactionId)
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        transactionRepository.deleteById(transactionId);
        transactionRepository.flush();
        updateAccountBalance(DEBIT_ACCOUNT_ID, new BigDecimal("1000.00"));
        updateAccountBalance(CREDIT_ACCOUNT_ID, new BigDecimal("500.00"));

        assertFalse(transactionRepository.existsById(transactionId));
        assertTrue(transactionRepository.findByTransactionRef(TRANSACTION_REFERENCE).isEmpty());
        log.info(
                "DELETE committed: transactionRef={}, deletedTransactionId={}; test accounts remain",
                TRANSACTION_REFERENCE,
                transactionId);
    }

    private BankTransaction requiredTransaction() {
        return transactionRepository.findByTransactionRef(TRANSACTION_REFERENCE)
                .orElseThrow(() -> new AssertionError(
                        "Run createAccountsAndTransaction before this operation"));
    }

    private void createAccountIfMissing(
            String accountId,
            String accountNumber,
            String customerId,
            BigDecimal availableBalance) {
        if (accountCount(accountId) > 0) return;

        LocalDateTime now = LocalDateTime.now();
        int inserted = entityManager.createNativeQuery("""
                        INSERT INTO account (
                            account_id, account_number, customer_id, product_id,
                            ownership_type, status, currency_code, available_balance,
                            opened_at, version_no, created_at, updated_at
                        ) VALUES (
                            :accountId, :accountNumber, :customerId, :productId,
                            :ownershipType, :status, :currencyCode, :availableBalance,
                            :openedAt, :versionNo, :createdAt, :updatedAt
                        )
                        """)
                .setParameter("accountId", accountId)
                .setParameter("accountNumber", accountNumber)
                .setParameter("customerId", customerId)
                .setParameter("productId", PRODUCT_ID)
                .setParameter("ownershipType", "INDIVIDUAL")
                .setParameter("status", "ACTIVE")
                .setParameter("currencyCode", "INR")
                .setParameter("availableBalance", availableBalance)
                .setParameter("openedAt", now)
                .setParameter("versionNo", 0L)
                .setParameter("createdAt", now)
                .setParameter("updatedAt", now)
                .executeUpdate();
        assertEquals(1, inserted);
    }

    private long accountCount(String accountId) {
        Number count = (Number) entityManager.createNativeQuery(
                        "SELECT COUNT(*) FROM account WHERE account_id = :accountId")
                .setParameter("accountId", accountId)
                .getSingleResult();
        return count.longValue();
    }

    private BigDecimal accountBalance(String accountId) {
        return (BigDecimal) entityManager.createNativeQuery(
                        "SELECT available_balance FROM account WHERE account_id = :accountId")
                .setParameter("accountId", accountId)
                .getSingleResult();
    }

    private void updateAccountBalance(String accountId, BigDecimal balance) {
        int updated = entityManager.createNativeQuery("""
                        UPDATE account
                        SET available_balance = :balance,
                            updated_at = :updatedAt,
                            version_no = version_no + 1
                        WHERE account_id = :accountId
                        """)
                .setParameter("balance", balance)
                .setParameter("updatedAt", LocalDateTime.now())
                .setParameter("accountId", accountId)
                .executeUpdate();
        assertEquals(1, updated);
    }

    private static AccountStatement statement(
            BankTransaction transaction,
            String accountId,
            StatementEntryType entryType,
            BigDecimal balanceAfter) {
        AccountStatement statement = new AccountStatement();
        statement.setTransaction(transaction);
        statement.setAccountId(accountId);
        statement.setEntryType(entryType);
        statement.setAmount(transaction.getAmount());
        statement.setCurrencyCode(transaction.getCurrencyCode());
        statement.setBalanceAfter(balanceAfter);
        return statement;
    }

    private static TransactionApproval approval(BankTransaction transaction) {
        TransactionApproval approval = new TransactionApproval();
        ReflectionTestUtils.setField(approval, "transaction", transaction);
        ReflectionTestUtils.setField(approval, "accountHolderAccountId", DEBIT_ACCOUNT_ID);
        ReflectionTestUtils.setField(approval, "accountHolderCustomerId", CUSTOMER_ID);
        ReflectionTestUtils.setField(approval, "approvalStatus", ApprovalStatus.APPROVED);
        ReflectionTestUtils.setField(approval, "approvalNote", "Connected database CRUD test");
        ReflectionTestUtils.setField(approval, "approvedAt", LocalDateTime.now());
        return approval;
    }

    private static TransactionEventOutbox outboxEvent(String transactionId) {
        TransactionEventOutbox event = new TransactionEventOutbox();
        ReflectionTestUtils.setField(event, "aggregateId", transactionId);
        ReflectionTestUtils.setField(event, "eventType", TransactionEventType.TRANSACTION_COMPLETED);
        ReflectionTestUtils.setField(
                event,
                "payloadJson",
                "{\"transactionId\":\"" + transactionId + "\",\"source\":\"connected-db-crud-test\"}");
        return event;
    }

    private static void assertMoneyEquals(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual));
    }
}
