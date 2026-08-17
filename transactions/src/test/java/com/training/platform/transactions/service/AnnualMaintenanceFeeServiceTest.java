package com.training.platform.transactions.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.training.platform.transactions.client.AccountsClient;
import com.training.platform.transactions.client.AnnualFeeAccountResponse;
import com.training.platform.transactions.entity.BankTransaction;
import com.training.platform.transactions.entity.TransactionStatus;
import com.training.platform.transactions.entity.TransactionType;
import com.training.platform.transactions.repository.BankTransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnnualMaintenanceFeeServiceTest {
    @Mock AccountsClient accountsClient;
    @Mock BankTransactionService transactionService;
    @Mock BankTransactionRepository transactionRepository;
    AnnualMaintenanceFeeService service;

    @BeforeEach void setUp() {
        service = new AnnualMaintenanceFeeService(accountsClient, transactionService, transactionRepository);
    }

    @Test void createsOneDeterministicTransactionOnTheAccountAnniversary() {
        when(accountsClient.annualFeeAccounts()).thenReturn(List.of(account(
                LocalDateTime.of(2025, 8, 17, 10, 0))));
        when(transactionService.initiate(any())).thenAnswer(invocation -> {
            BankTransaction transaction = invocation.getArgument(0);
            transaction.setTransactionStatus(TransactionStatus.COMPLETED);
            return transaction;
        });

        assertEquals(1, service.process(LocalDate.of(2026, 8, 17)));

        ArgumentCaptor<BankTransaction> captor = ArgumentCaptor.forClass(BankTransaction.class);
        verify(transactionService).initiate(captor.capture());
        BankTransaction fee = captor.getValue();
        assertEquals("AF2026-00000000000000000000000000000101", fee.getTransactionRef());
        assertEquals(TransactionType.ANNUAL_MAINTENANCE_FEE, fee.getTransactionType());
        assertEquals(new BigDecimal("1200.00"), fee.getAmount());
        assertEquals(new BigDecimal("1200.00"), fee.getFeeAmount());
    }

    @Test void doesNotChargeBeforeTheAnniversary() {
        when(accountsClient.annualFeeAccounts()).thenReturn(List.of(account(
                LocalDateTime.of(2025, 8, 17, 10, 0))));

        assertEquals(0, service.process(LocalDate.of(2026, 8, 16)));
        verifyNoInteractions(transactionService);
    }

    @Test void catchesUpAfterTheAnniversaryAndUsesFebruaryTwentyEighthForLeapDayAccounts() {
        LocalDateTime openedAt = LocalDateTime.of(2024, 2, 29, 10, 0);
        assertTrue(AnnualMaintenanceFeeService.feeDue(openedAt, LocalDate.of(2025, 2, 28)));
        assertTrue(AnnualMaintenanceFeeService.feeDue(openedAt, LocalDate.of(2025, 3, 1)));
    }

    @Test void skipsACompletedFeeForTheSameAccountAndYear() {
        when(accountsClient.annualFeeAccounts()).thenReturn(List.of(account(
                LocalDateTime.of(2025, 8, 17, 10, 0))));
        BankTransaction completed = new BankTransaction();
        completed.setTransactionStatus(TransactionStatus.COMPLETED);
        when(transactionRepository.findByTransactionRef(
                "AF2026-00000000000000000000000000000101"))
                .thenReturn(java.util.Optional.of(completed));

        assertEquals(0, service.process(LocalDate.of(2026, 8, 18)));
        verifyNoInteractions(transactionService);
    }

    private static AnnualFeeAccountResponse account(LocalDateTime openedAt) {
        return new AnnualFeeAccountResponse("00000000-0000-0000-0000-000000000101",
                "customer-1", "product-1", "CURRENT", "INR", new BigDecimal("6000.00"),
                openedAt, new BigDecimal("1200.00"));
    }
}
