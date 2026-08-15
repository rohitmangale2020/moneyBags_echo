package com.training.platform.accounts.service;

import com.training.platform.auditclient.AuditClient;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;

import com.training.platform.accounts.entity.Account;
import com.training.platform.accounts.entity.AccountStatus;
import com.training.platform.accounts.entity.OwnershipType;
import com.training.platform.accounts.repository.AccountHolderRepository;
import com.training.platform.accounts.repository.AccountBalanceOperationRepository;
import com.training.platform.accounts.repository.AccountRepository;
import com.training.platform.accounts.repository.AccountStatusHistoryRepository;
import com.training.platform.accounts.repository.AccountTransferOperationRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;
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
    @Mock private Account account;
    private AccountService accountService;

    @BeforeEach void setUp() {
        accountService = new AccountService(accountRepository, accountHolderRepository,
                accountStatusHistoryRepository, transferOperationRepository, balanceOperationRepository,
                auditClient);
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
        when(account.getOwnershipType()).thenReturn(OwnershipType.INDIVIDUAL);
        when(account.getStatus()).thenReturn(AccountStatus.ACTIVE);
        when(account.getAccountNumber()).thenReturn("ACC-1");
        when(account.getCustomerId()).thenReturn("customer-1");
        when(account.getProductId()).thenReturn("product-1");
        when(account.getCurrencyCode()).thenReturn("INR");
        when(account.getAvailableBalance()).thenReturn(BigDecimal.ZERO);
        when(accountRepository.save(account)).thenReturn(account);
        assertSame(account, accountService.create(account));
        verify(account).setAccountNumber(argThat(number -> number != null && number.matches("\\d{12}")));
        verify(accountRepository).save(account);
        verify(accountHolderRepository).save(org.mockito.ArgumentMatchers.any());
        verify(accountStatusHistoryRepository).save(org.mockito.ArgumentMatchers.any());
    }
}
