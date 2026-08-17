package com.training.platform.transactions.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import com.training.platform.auditclient.AuditClient;
import com.training.platform.transactions.client.AccountDetailsResponse;
import com.training.platform.transactions.client.AccountsClient;
import com.training.platform.transactions.dto.FixedDepositOpenRequest;
import com.training.platform.transactions.entity.BankTransaction;
import com.training.platform.transactions.entity.FixedDepositContract;
import com.training.platform.transactions.entity.FixedDepositStatus;
import com.training.platform.transactions.entity.TransactionStatus;
import com.training.platform.transactions.entity.TransactionType;
import com.training.platform.transactions.repository.FixedDepositContractRepository;
import jakarta.persistence.Column;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FixedDepositServiceTest {
    @Mock FixedDepositContractRepository contracts;
    @Mock AccountsClient accounts;
    @Mock BankTransactionService transactions;
    @Mock AuditClient audit;
    FixedDepositService service;

    @BeforeEach void setUp() {
        service = new FixedDepositService(contracts, accounts, transactions, audit, new BigDecimal("1.00"));
        org.mockito.Mockito.lenient().when(contracts.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test void openingFundsFdFromCustomersTransactionalAccount() {
        when(contracts.findByFdAccountId("fd-1")).thenReturn(Optional.empty());
        when(accounts.getAccount("fd-1")).thenReturn(account("fd-1", "FD", BigDecimal.ZERO, 12, 3));
        when(accounts.getAccount("source-1")).thenReturn(account("source-1", "SAVINGS",
                new BigDecimal("50000.00"), null, null));
        when(transactions.initiate(any())).thenAnswer(invocation -> {
            BankTransaction transaction = invocation.getArgument(0);
            ReflectionTestUtils.setField(transaction, "transactionId", "funding-txn");
            transaction.setTransactionStatus(TransactionStatus.COMPLETED);
            return transaction;
        });

        FixedDepositContract contract = service.open(new FixedDepositOpenRequest(
                "fd-1", "source-1", "source-1", new BigDecimal("10000.00")));

        assertEquals(FixedDepositStatus.ACTIVE, contract.getStatus());
        assertEquals(LocalDate.now().plusMonths(12), contract.getMaturityDate());
        assertEquals(LocalDate.now(), contract.getLockInUntil());
        assertEquals(0, contract.getLockInPeriodMonths());
        assertEquals(true, contract.getPrematureWithdrawalAllowed());
        assertEquals("funding-txn", contract.getFundingTransactionId());
        ArgumentCaptor<BankTransaction> funding = ArgumentCaptor.forClass(BankTransaction.class);
        verify(transactions).initiate(funding.capture());
        assertEquals(TransactionType.FIXED_DEPOSIT_FUNDING, funding.getValue().getTransactionType());
        assertEquals("source-1", funding.getValue().getDebitAccountId());
        assertEquals("fd-1", funding.getValue().getCreditAccountId());
    }

    @Test void openingRejectsASeparatePayoutAccount() {
        when(contracts.findByFdAccountId("fd-1")).thenReturn(Optional.empty());
        when(accounts.getAccount("fd-1")).thenReturn(account("fd-1", "FD", BigDecimal.ZERO, 12, 3));
        when(accounts.getAccount("source-1")).thenReturn(account("source-1", "SAVINGS",
                new BigDecimal("50000.00"), null, null));
        when(accounts.getAccount("other-1")).thenReturn(account("other-1", "SAVINGS",
                new BigDecimal("50000.00"), null, null));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.open(new FixedDepositOpenRequest(
                        "fd-1", "source-1", "other-1", new BigDecimal("10000.00"))));

        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("same account"));
    }

    @Test void maturityCreditsPrincipalAndCalculatedInterestToOriginalFundingAccount() {
        LocalDate openedOn = LocalDate.of(2025, 1, 1);
        FixedDepositContract contract = FixedDepositContract.open(
                "fd-1", "source-1", "payout-1", "customer-1", "34",
                new BigDecimal("100000.00"), new BigDecimal("6.50"),
                12, 3, "CREDIT_TO_ACCOUNT", true, openedOn);
        contract.recordFunding("funding-txn");
        when(contracts.findById(contract.getContractId())).thenReturn(Optional.of(contract));
        when(accounts.getAccount("source-1")).thenReturn(account("source-1", "SAVINGS",
                new BigDecimal("10000.00"), null, null));
        when(transactions.initiate(any())).thenAnswer(invocation -> {
            BankTransaction transaction = invocation.getArgument(0);
            String id = transaction.getTransactionType() == TransactionType.FIXED_DEPOSIT_MATURITY
                    ? "principal-txn" : "interest-txn";
            ReflectionTestUtils.setField(transaction, "transactionId", id);
            transaction.setTransactionStatus(TransactionStatus.COMPLETED);
            return transaction;
        });

        FixedDepositContract matured = service.mature(contract.getContractId(), LocalDate.of(2026, 1, 1));

        assertEquals(FixedDepositStatus.MATURED, matured.getStatus());
        assertEquals(new BigDecimal("6500.00"), matured.getInterestPaid());
        assertEquals("source-1", matured.getPayoutAccountId());
        ArgumentCaptor<BankTransaction> postings = ArgumentCaptor.forClass(BankTransaction.class);
        verify(transactions, times(2)).initiate(postings.capture());
        BankTransaction interest = postings.getAllValues().get(0);
        BankTransaction principal = postings.getAllValues().get(1);
        assertEquals(TransactionType.FIXED_DEPOSIT_INTEREST_CREDIT, interest.getTransactionType());
        assertEquals("source-1", interest.getCreditAccountId());
        assertNull(interest.getDebitAccountId());
        assertEquals(new BigDecimal("6500.00"), interest.getAmount());
        assertEquals(TransactionType.FIXED_DEPOSIT_MATURITY, principal.getTransactionType());
        assertEquals("fd-1", principal.getDebitAccountId());
        assertEquals("source-1", principal.getCreditAccountId());
        assertEquals(new BigDecimal("100000.00"), principal.getAmount());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> auditDetails = ArgumentCaptor.forClass(Map.class);
        verify(audit).success(eq("transactions"), eq("FIXED_DEPOSIT_MATURED"),
                eq("Fixed deposit matured on 2026-01-01; principal and interest were credited"
                        + " to the original funding account"), auditDetails.capture());
        assertEquals("SYSTEM", auditDetails.getValue().get("actorId"));
        assertEquals("SYSTEM", auditDetails.getValue().get("actorType"));
        assertEquals(LocalDate.of(2026, 1, 1), auditDetails.getValue().get("maturityDate"));
    }

    @Test void prematureWithdrawalHasNoLockInAndPaysReducedInterestToFundingAccount() {
        LocalDate openedOn = LocalDate.of(2025, 1, 1);
        FixedDepositContract contract = FixedDepositContract.open(
                "fd-1", "source-1", "legacy-payout", "customer-1", "34",
                new BigDecimal("100000.00"), new BigDecimal("6.50"),
                12, 6, "CREDIT_TO_ACCOUNT", false, openedOn);
        contract.recordFunding("funding-txn");
        when(contracts.findById(contract.getContractId())).thenReturn(Optional.of(contract));
        when(accounts.getAccount("source-1")).thenReturn(account("source-1", "SAVINGS",
                new BigDecimal("10000.00"), null, null));
        when(transactions.initiate(any())).thenAnswer(invocation -> {
            BankTransaction transaction = invocation.getArgument(0);
            ReflectionTestUtils.setField(transaction, "transactionId", "txn-" + transaction.getTransactionType());
            transaction.setTransactionStatus(TransactionStatus.COMPLETED);
            return transaction;
        });

        FixedDepositContract closed = service.closePrematurely(
                contract.getContractId(), LocalDate.of(2025, 2, 1));

        assertEquals(FixedDepositStatus.PREMATURELY_CLOSED, closed.getStatus());
        assertEquals(new BigDecimal("467.12"), closed.getInterestPaid());
        assertEquals("source-1", closed.getPayoutAccountId());
        ArgumentCaptor<BankTransaction> postings = ArgumentCaptor.forClass(BankTransaction.class);
        verify(transactions, times(2)).initiate(postings.capture());
        assertEquals(TransactionType.FIXED_DEPOSIT_INTEREST_CREDIT,
                postings.getAllValues().get(0).getTransactionType());
        assertEquals(TransactionType.FIXED_DEPOSIT_PREMATURE_CLOSURE,
                postings.getAllValues().get(1).getTransactionType());
        assertEquals("source-1", postings.getAllValues().get(1).getCreditAccountId());
    }

    @Test void failedPrematureWithdrawalCanBeRetriedWithoutCreatingAnotherContract() {
        LocalDate openedOn = LocalDate.now();
        FixedDepositContract contract = FixedDepositContract.open(
                "fd-1", "source-1", "source-1", "customer-1", "34",
                new BigDecimal("10000.00"), new BigDecimal("6.50"),
                12, 0, "CREDIT_TO_ACCOUNT", true, openedOn);
        contract.recordFunding("funding-txn");
        when(contracts.findById(contract.getContractId())).thenReturn(Optional.of(contract));
        when(accounts.getAccount("source-1")).thenReturn(account("source-1", "SAVINGS",
                new BigDecimal("10000.00"), null, null));
        AtomicInteger attempts = new AtomicInteger();
        when(transactions.initiate(any())).thenAnswer(invocation -> {
            BankTransaction transaction = invocation.getArgument(0);
            ReflectionTestUtils.setField(transaction, "transactionId", "closure-txn");
            transaction.setTransactionStatus(attempts.getAndIncrement() == 0
                    ? TransactionStatus.FAILED : TransactionStatus.COMPLETED);
            return transaction;
        });

        FixedDepositContract firstAttempt = service.closePrematurely(contract.getContractId(), openedOn);
        assertEquals(FixedDepositStatus.ACTIVE, firstAttempt.getStatus());
        FixedDepositContract retry = service.closePrematurely(contract.getContractId(), openedOn);

        assertEquals(FixedDepositStatus.PREMATURELY_CLOSED, retry.getStatus());
        ArgumentCaptor<BankTransaction> postings = ArgumentCaptor.forClass(BankTransaction.class);
        verify(transactions, times(2)).initiate(postings.capture());
        assertEquals(postings.getAllValues().get(0).getTransactionRef(),
                postings.getAllValues().get(1).getTransactionRef());
    }

    @Test void prematureClosureTypeFitsMappedTransactionTypeColumn() throws Exception {
        Column mapping = BankTransaction.class.getDeclaredField("transactionType")
                .getAnnotation(Column.class);

        org.junit.jupiter.api.Assertions.assertTrue(
                TransactionType.FIXED_DEPOSIT_PREMATURE_CLOSURE.name().length() <= mapping.length());
    }

    private static AccountDetailsResponse account(String id, String type, BigDecimal balance,
                                                  Integer tenure, Integer lockIn) {
        return new AccountDetailsResponse(id, "ACC-" + id, "customer-1", "34", type,
                "FD".equals(type) ? new BigDecimal("10000.00") : BigDecimal.ZERO,
                null, new BigDecimal("6.50"), tenure, lockIn,
                "FD".equals(type) ? "CREDIT_TO_ACCOUNT" : null, true, null, null,
                "INDIVIDUAL", "ACTIVE", "INR", balance, null, null, 0L, null, null);
    }
}
