package com.training.platform.accounts.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.training.platform.accounts.entity.Account;
import com.training.platform.accounts.repository.AccountRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {
    @Mock private AccountRepository accountRepository;
    @Mock private Account account;
    private AccountService accountService;

    @BeforeEach void setUp() { accountService = new AccountService(accountRepository); }

    @Test void returnsAccountWhenIdExists() {
        when(accountRepository.findById("account-1")).thenReturn(Optional.of(account));
        assertSame(account, accountService.getById("account-1"));
    }

    @Test void throwsWhenIdDoesNotExist() {
        when(accountRepository.findById("missing")).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> accountService.getById("missing"));
    }

    @Test void savesAccountWhenCreating() {
        when(accountRepository.save(account)).thenReturn(account);
        assertSame(account, accountService.create(account));
        verify(accountRepository).save(account);
    }
}
