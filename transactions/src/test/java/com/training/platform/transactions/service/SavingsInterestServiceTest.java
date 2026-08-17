package com.training.platform.transactions.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.training.platform.transactions.client.AccountsClient;
import com.training.platform.transactions.client.InterestBearingAccountResponse;
import com.training.platform.transactions.entity.BankTransaction;
import com.training.platform.transactions.entity.TransactionStatus;
import com.training.platform.transactions.entity.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SavingsInterestServiceTest {
    @Mock AccountsClient accountsClient;
    @Mock BankTransactionService transactionService;
    SavingsInterestService service;

    @BeforeEach void setUp() { service = new SavingsInterestService(accountsClient, transactionService); }

    @Test void createsCompletedInterestCreditForDueSavingsAccount() {
        LocalDate periodEnd = LocalDate.of(2026, 8, 31);
        InterestBearingAccountResponse account = new InterestBearingAccountResponse(
                "00000000-0000-0000-0000-000000000101", "customer-1", "36", "SAVINGS",
                "INR", new BigDecimal("100000.00"), new BigDecimal("4.00"),
                LocalDate.of(2026, 7, 31), periodEnd);
        when(accountsClient.interestDue(periodEnd)).thenReturn(List.of(account));
        when(transactionService.initiate(any())).thenAnswer(invocation -> {
            BankTransaction transaction = invocation.getArgument(0);
            transaction.setTransactionStatus(TransactionStatus.COMPLETED);
            return transaction;
        });

        assertEquals(1, service.processDue(periodEnd));

        ArgumentCaptor<BankTransaction> transaction = ArgumentCaptor.forClass(BankTransaction.class);
        verify(transactionService).initiate(transaction.capture());
        assertEquals(TransactionType.INTEREST_CREDIT, transaction.getValue().getTransactionType());
        assertEquals(account.accountId(), transaction.getValue().getCreditAccountId());
        assertNull(transaction.getValue().getDebitAccountId());
        assertEquals(new BigDecimal("339.73"), transaction.getValue().getAmount());
        assertEquals(periodEnd, transaction.getValue().getInterestPeriodEnd());
    }

    @Test void advancesZeroBalancePeriodWithoutCreatingMoneyTransaction() {
        LocalDate periodEnd = LocalDate.of(2026, 8, 31);
        InterestBearingAccountResponse account = new InterestBearingAccountResponse(
                "account-1", "customer-1", "36", "SAVINGS", "INR", BigDecimal.ZERO,
                new BigDecimal("4.00"), LocalDate.of(2026, 7, 31), periodEnd);
        when(accountsClient.interestDue(periodEnd)).thenReturn(List.of(account));

        assertEquals(1, service.processDue(periodEnd));
        verify(accountsClient).markInterestProcessed("account-1", periodEnd, "SI2608-account1");
        verify(transactionService, never()).initiate(any());
    }
}
