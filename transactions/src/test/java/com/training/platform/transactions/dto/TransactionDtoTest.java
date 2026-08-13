package com.training.platform.transactions.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.training.platform.transactions.entity.AccountStatement;
import com.training.platform.transactions.entity.BankTransaction;
import com.training.platform.transactions.entity.StatementEntryType;
import com.training.platform.transactions.entity.TransactionStatus;
import com.training.platform.transactions.entity.TransactionType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TransactionDtoTest {
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void validTransactionRequestHasNoValidationErrors() {
        assertTrue(validator.validate(validTransactionRequest()).isEmpty());
    }

    @Test
    void invalidTransactionRequestReportsEveryInvalidField() {
        TransactionRequest request = new TransactionRequest(
                " ", null, null, "debit-1", "credit-1", null,
                BigDecimal.ZERO, "12", new BigDecimal("-0.01"),
                null, null, null, null, null);

        Set<String> fields = fields(validator.validate(request));

        assertTrue(fields.containsAll(Set.of(
                "transactionRef", "transactionType", "transactionStatus",
                "amount", "currencyCode", "feeAmount")));
    }

    @Test
    void validStatementRequestHasNoValidationErrors() {
        StatementRequest request = new StatementRequest(
                "txn-1", "account-1", StatementEntryType.DEBIT,
                new BigDecimal("25.00"), "INR", new BigDecimal("975.00"));

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void invalidStatementRequestReportsEveryInvalidField() {
        StatementRequest request = new StatementRequest(
                " ", "", null, BigDecimal.ZERO, "RUPEES", null);

        Set<String> fields = fields(validator.validate(request));

        assertTrue(fields.containsAll(Set.of(
                "transactionId", "accountId", "entryType", "amount",
                "currencyCode", "balanceAfter")));
    }

    @Test
    void transactionResponseMapsEveryEntityField() {
        LocalDateTime initiatedAt = LocalDateTime.of(2025, 1, 1, 10, 0);
        LocalDateTime completedAt = initiatedAt.plusMinutes(2);
        BankTransaction transaction = new BankTransaction();
        ReflectionTestUtils.setField(transaction, "transactionId", "txn-1");
        ReflectionTestUtils.setField(transaction, "initiatedAt", initiatedAt);
        transaction.setTransactionRef("REF-1");
        transaction.setTransactionType(TransactionType.TRANSFER);
        transaction.setTransactionStatus(TransactionStatus.COMPLETED);
        transaction.setDebitAccountId("account-a");
        transaction.setCreditAccountId("account-b");
        transaction.setExternalBeneficiary("Beneficiary");
        transaction.setDescription("INTERNAL TRANSFER FROM A TO B");
        transaction.setAmount(new BigDecimal("100.00"));
        transaction.setCurrencyCode("INR");
        transaction.setFeeAmount(new BigDecimal("2.00"));
        transaction.setInitiatedByCustomerId("customer-1");
        transaction.setInitiatedByUserId("user-1");
        transaction.setCompletedAt(completedAt);
        transaction.setFailureCode(null);
        transaction.setFailureReason(null);

        TransactionResponse response = TransactionResponse.from(transaction);

        assertEquals("txn-1", response.transactionId());
        assertEquals("REF-1", response.transactionRef());
        assertEquals(TransactionType.TRANSFER, response.transactionType());
        assertEquals(TransactionStatus.COMPLETED, response.transactionStatus());
        assertEquals("account-a", response.debitAccountId());
        assertEquals("account-b", response.creditAccountId());
        assertEquals("Beneficiary", response.externalBeneficiary());
        assertEquals("INTERNAL TRANSFER FROM A TO B", response.description());
        assertEquals(new BigDecimal("100.00"), response.amount());
        assertEquals("INR", response.currencyCode());
        assertEquals(new BigDecimal("2.00"), response.feeAmount());
        assertEquals("customer-1", response.initiatedByCustomerId());
        assertEquals("user-1", response.initiatedByUserId());
        assertEquals(initiatedAt, response.initiatedAt());
        assertEquals(completedAt, response.completedAt());
    }

    @Test
    void statementResponseMapsEveryEntityField() {
        LocalDateTime postedAt = LocalDateTime.of(2025, 2, 3, 11, 30);
        BankTransaction transaction = new BankTransaction();
        ReflectionTestUtils.setField(transaction, "transactionId", "txn-1");
        transaction.setTransactionRef("REF-1");
        transaction.setTransactionType(TransactionType.DEPOSIT);
        AccountStatement statement = new AccountStatement();
        ReflectionTestUtils.setField(statement, "statementId", "statement-1");
        ReflectionTestUtils.setField(statement, "postedAt", postedAt);
        statement.setTransaction(transaction);
        statement.setAccountId("account-1");
        statement.setEntryType(StatementEntryType.CREDIT);
        statement.setAmount(new BigDecimal("50.00"));
        statement.setDescription("DEPOSIT BY ROHIT MANGALE | REF REF-1");
        statement.setDepositAmount(new BigDecimal("50.00"));
        statement.setCurrencyCode("INR");
        statement.setBalanceAfter(new BigDecimal("1050.00"));
        statement.setClosingBalance(new BigDecimal("1050.00"));

        StatementResponse response = StatementResponse.from(statement);

        assertEquals("statement-1", response.statementId());
        assertEquals("txn-1", response.transactionId());
        assertEquals("account-1", response.accountId());
        assertEquals("DEPOSIT BY ROHIT MANGALE | REF REF-1", response.description());
        assertEquals(new BigDecimal("50.00"), response.depositAmount());
        assertEquals(new BigDecimal("1050.00"), response.closingBalance());
        assertEquals(StatementEntryType.CREDIT, response.entryType());
        assertEquals(new BigDecimal("50.00"), response.amount());
        assertEquals("INR", response.currencyCode());
        assertEquals(new BigDecimal("1050.00"), response.balanceAfter());
        assertEquals(postedAt, response.postedAt());
    }

    private static TransactionRequest validTransactionRequest() {
        return new TransactionRequest(
                "REF-1", TransactionType.TRANSFER, TransactionStatus.INITIATED,
                "account-a", "account-b", null, new BigDecimal("100.00"),
                "INR", BigDecimal.ZERO, "customer-1", null,
                null, null, null);
    }

    private static Set<String> fields(Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
