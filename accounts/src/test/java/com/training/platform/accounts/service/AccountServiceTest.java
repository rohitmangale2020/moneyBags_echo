package com.training.platform.accounts.service;

import com.training.platform.auditclient.AuditClient;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;

import com.training.platform.accounts.dto.AccountAdjustmentRequest;
import com.training.platform.accounts.entity.Account;
import com.training.platform.accounts.entity.AccountStatus;
import com.training.platform.accounts.entity.OwnershipType;
import com.training.platform.accounts.client.ProductsClient;
import com.training.platform.accounts.client.ProductRulesResponse;
import com.training.platform.accounts.client.FixedDepositsClient;
import com.training.platform.accounts.client.FixedDepositDependencyResponse;
import com.training.platform.accounts.repository.AccountHolderRepository;
import com.training.platform.accounts.repository.AccountBalanceOperationRepository;
import com.training.platform.accounts.repository.AccountRepository;
import com.training.platform.accounts.repository.AccountStatusHistoryRepository;
import com.training.platform.accounts.repository.AccountTransferOperationRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {
    @Mock private AccountRepository accountRepository;
    @Mock private AccountHolderRepository accountHolderRepository;
    @Mock private AccountStatusHistoryRepository accountStatusHistoryRepository;
    @Mock private AccountTransferOperationRepository transferOperationRepository;
    @Mock private AccountBalanceOperationRepository balanceOperationRepository;
    @Mock private AuditClient auditClient;
    @Mock private ProductsClient productsClient;
    @Mock private FixedDepositsClient fixedDepositsClient;
    @Mock private Account account;
    private AccountService accountService;

    @BeforeEach void setUp() {
        accountService = new AccountService(accountRepository, accountHolderRepository,
                accountStatusHistoryRepository, transferOperationRepository, balanceOperationRepository,
                auditClient, productsClient, fixedDepositsClient);
    }

    @Test void returnsAccountWhenIdExists() {
        when(accountRepository.findById("account-1")).thenReturn(Optional.of(account));
        assertSame(account, accountService.getById("account-1"));
    }

    @Test void throwsWhenIdDoesNotExist() {
        when(accountRepository.findById("missing")).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> accountService.getById("missing"));
    }

    @Test void returnsAllAccountsNewestFirstFromRepository() {
        List<Account> accounts = List.of(account);
        when(accountRepository.findAllByOrderByCreatedAtDesc()).thenReturn(accounts);

        assertSame(accounts, accountService.getAllAccounts());
        verify(accountRepository).findAllByOrderByCreatedAtDesc();
    }

    @Test void savesAccountWhenCreating() {
        Account created = new Account();
        created.setCustomerId("customer-1");
        created.setProductId("1");
        created.setOwnershipType(OwnershipType.INDIVIDUAL);
        created.setStatus(AccountStatus.ACTIVE);
        created.setCurrencyCode("INR");
        created.setAvailableBalance(BigDecimal.ZERO);
        when(productsClient.getById("1")).thenReturn(savingsProduct());
        when(accountRepository.save(created)).thenReturn(created);
        assertSame(created, accountService.create(created));
        org.junit.jupiter.api.Assertions.assertTrue(created.getAccountNumber().matches("\\d{12}"));
        verify(accountRepository).save(created);
        verify(accountHolderRepository).save(org.mockito.ArgumentMatchers.any());
        verify(accountStatusHistoryRepository).save(org.mockito.ArgumentMatchers.any());
    }

    @Test void rejectsDirectOpeningBalanceSoItCannotBypassTransactionsAndLedger() {
        Account created = savingsAccount("account-1", new BigDecimal("10000.00"), AccountStatus.ACTIVE);
        when(productsClient.getById("1")).thenReturn(savingsProduct());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> accountService.create(created));

        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("opening-deposit transaction"));
    }

    @Test void refusesToCloseTransactionalAccountLinkedToAnActiveFixedDeposit() {
        Account existing = savingsAccount("account-1", BigDecimal.ZERO, AccountStatus.ACTIVE);
        Account requested = savingsAccount("account-1", BigDecimal.ZERO, AccountStatus.CLOSED);
        when(accountRepository.findById("account-1")).thenReturn(Optional.of(existing));
        when(fixedDepositsClient.activeForAccount("account-1")).thenReturn(List.of(
                new FixedDepositDependencyResponse("contract-1", "fd-1", "account-1", "account-1", "ACTIVE")));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> accountService.update("account-1", requested));

        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("active fixed deposit"));
        assertSame(AccountStatus.ACTIVE, existing.getStatus());
    }

    @Test void closesZeroBalanceTransactionalAccountWithoutFixedDeposit() {
        Account existing = savingsAccount("account-1", BigDecimal.ZERO, AccountStatus.ACTIVE);
        Account requested = savingsAccount("account-1", BigDecimal.ZERO, AccountStatus.CLOSED);
        when(accountRepository.findById("account-1")).thenReturn(Optional.of(existing));
        when(fixedDepositsClient.activeForAccount("account-1")).thenReturn(List.of());
        when(accountRepository.save(existing)).thenReturn(existing);

        Account saved = accountService.update("account-1", requested);

        assertSame(AccountStatus.CLOSED, saved.getStatus());
    }

    @Test void refusesToCloseAccountWhileItStillHasMoney() {
        Account existing = savingsAccount("account-1", new BigDecimal("25.00"), AccountStatus.ACTIVE);
        Account requested = savingsAccount("account-1", new BigDecimal("25.00"), AccountStatus.CLOSED);
        when(accountRepository.findById("account-1")).thenReturn(Optional.of(existing));
        when(fixedDepositsClient.activeForAccount("account-1")).thenReturn(List.of());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> accountService.update("account-1", requested));

        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("complete account balance"));
        assertSame(AccountStatus.ACTIVE, existing.getStatus());
    }

    @Test void savingsInterestAuditUsesSystemMakerAndExplainsThePeriodEnd() {
        Account target = savingsAccount("account-1", new BigDecimal("1000.00"), AccountStatus.ACTIVE);
        target.setAnnualInterestRate(new BigDecimal("4.00"));
        when(balanceOperationRepository.findByTransactionRef("SI2608-account1"))
                .thenReturn(Optional.empty());
        when(accountRepository.findByIdForUpdate("account-1")).thenReturn(Optional.of(target));
        when(accountRepository.findById("account-1")).thenReturn(Optional.of(target));
        when(accountRepository.save(target)).thenReturn(target);
        when(balanceOperationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        AccountAdjustmentRequest request = new AccountAdjustmentRequest("SI2608-account1",
                AccountAdjustmentRequest.AdjustmentType.INTEREST_CREDIT, new BigDecimal("40.00"),
                "INR", LocalDate.of(2026, 8, 31));

        accountService.adjust("account-1", request);

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Map<String, Object>> details =
                org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(auditClient).success(eq("accounts"), eq("BALANCE_CREDITED"),
                eq("Savings interest credited for period ending 2026-08-31"), details.capture());
        org.junit.jupiter.api.Assertions.assertEquals("SYSTEM", details.getValue().get("actorId"));
        org.junit.jupiter.api.Assertions.assertEquals("SYSTEM", details.getValue().get("actorType"));
        org.junit.jupiter.api.Assertions.assertEquals("INTEREST_CREDIT",
                details.getValue().get("postingType"));
        org.junit.jupiter.api.Assertions.assertEquals(LocalDate.of(2026, 8, 31),
                details.getValue().get("effectiveDate"));
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("1040.00"),
                target.getAvailableBalance());
    }

    private static Account savingsAccount(String id, BigDecimal balance, AccountStatus status) {
        Account account = new Account();
        org.springframework.test.util.ReflectionTestUtils.setField(account, "accountId", id);
        account.setAccountNumber("123456789012");
        account.setCustomerId("customer-1");
        account.setProductId("1");
        account.setProductTypeCode("SAVINGS");
        account.setOwnershipType(OwnershipType.INDIVIDUAL);
        account.setStatus(status);
        account.setCurrencyCode("INR");
        account.setAvailableBalance(balance);
        account.setMinimumBalance(BigDecimal.ZERO);
        return account;
    }

    private static ProductRulesResponse savingsProduct() {
        return new ProductRulesResponse(1L, "SA", "Savings", "SAVINGS", "Savings", null,
                null, BigDecimal.ZERO, new BigDecimal("1000000"), "INR", "ACTIVE", 0L,
                null, null, null, null, new ProductRulesResponse.Rate(new BigDecimal("4.00")),
                new ProductRulesResponse.Term(null, null, null, null, null, false), null);
    }
}
