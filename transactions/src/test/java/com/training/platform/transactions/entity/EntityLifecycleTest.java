package com.training.platform.transactions.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class EntityLifecycleTest {

    @Test
    void bankTransactionGeneratesIdentityAndInitiationTime() {
        BankTransaction transaction = new BankTransaction();

        transaction.beforeInsert();

        assertNotNull(transaction.getTransactionId());
        assertEquals(36, transaction.getTransactionId().length());
        assertNotNull(transaction.getInitiatedAt());
        assertEquals(BigDecimal.ZERO, transaction.getFeeAmount());
    }

    @Test
    void bankTransactionKeepsAnExistingInitiationTime() {
        BankTransaction transaction = new BankTransaction();
        LocalDateTime initiatedAt = LocalDateTime.of(2025, 1, 2, 3, 4);
        ReflectionTestUtils.setField(transaction, "initiatedAt", initiatedAt);

        transaction.beforeInsert();

        assertEquals(initiatedAt, transaction.getInitiatedAt());
    }

    @Test
    void accountStatementGeneratesIdentityAndPostingTime() {
        AccountStatement statement = new AccountStatement();

        statement.beforeInsert();

        assertNotNull(statement.getStatementId());
        assertEquals(36, statement.getStatementId().length());
        assertNotNull(statement.getPostedAt());
    }

    @Test
    void transactionApprovalGeneratesIdentity() {
        TransactionApproval approval = new TransactionApproval();

        approval.beforeInsert();

        String approvalId = (String) ReflectionTestUtils.getField(approval, "approvalId");
        assertNotNull(approvalId);
        assertEquals(36, approvalId.length());
    }

    @Test
    void outboxEventGeneratesIdentityAndOccurrenceTime() {
        TransactionEventOutbox event = new TransactionEventOutbox();

        event.beforeInsert();

        String eventId = (String) ReflectionTestUtils.getField(event, "eventId");
        LocalDateTime occurredAt = (LocalDateTime) ReflectionTestUtils.getField(event, "occurredAt");
        Integer retryCount = (Integer) ReflectionTestUtils.getField(event, "retryCount");
        assertNotNull(eventId);
        assertEquals(36, eventId.length());
        assertNotNull(occurredAt);
        assertEquals(0, retryCount);
        assertTrue(occurredAt.isBefore(LocalDateTime.now().plusSeconds(1)));
    }
}
