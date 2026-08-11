package com.training.platform.accounts.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.training.platform.accounts.entity.Account;
import com.training.platform.accounts.entity.AccountStatus;
import com.training.platform.accounts.entity.OwnershipType;
import com.training.platform.accounts.service.AccountService;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AccountControllerTest {
    private AccountService accountService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        accountService = mock(AccountService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AccountController(accountService))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void getsAccountById() throws Exception {
        Account account = account("ACC-1", "customer-1");
        when(accountService.getById("account-1")).thenReturn(account);

        mockMvc.perform(get("/api/accounts/account-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("account-1"));
        verify(accountService).getById("account-1");
    }

    @Test
    void returnsNotFoundForMissingAccount() throws Exception {
        when(accountService.getById("missing")).thenThrow(new EntityNotFoundException("Account not found: missing"));
        mockMvc.perform(get("/api/accounts/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void findsAccountsByCustomer() throws Exception {
        Account account = account("ACC-1", "customer-1");
        when(accountService.getByCustomerId("customer-1")).thenReturn(List.of(account));
        mockMvc.perform(get("/api/accounts").param("customerId", "customer-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].accountNumber").value("ACC-1"));
    }

    @Test
    void createsAndUpdatesAccount() throws Exception {
        Account account = account("ACC-1", "customer-1");
        when(accountService.create(any(Account.class))).thenReturn(account);
        when(accountService.update(org.mockito.ArgumentMatchers.eq("account-1"), any(Account.class))).thenReturn(account);

        mockMvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content(requestBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountNumber").value("ACC-1"));
        mockMvc.perform(put("/api/accounts/account-1").contentType(MediaType.APPLICATION_JSON).content(requestBody()))
                .andExpect(status().isOk());
        verify(accountService).create(any(Account.class));
        verify(accountService).update(org.mockito.ArgumentMatchers.eq("account-1"), any(Account.class));
    }

    @Test
    void rejectsInvalidCreateRequest() throws Exception {
        mockMvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    private Account account(String number, String customerId) {
        Account account = mock(Account.class);
        when(account.getAccountId()).thenReturn("account-1");
        when(account.getAccountNumber()).thenReturn(number);
        when(account.getCustomerId()).thenReturn(customerId);
        when(account.getProductId()).thenReturn("product-1");
        when(account.getOwnershipType()).thenReturn(OwnershipType.INDIVIDUAL);
        when(account.getStatus()).thenReturn(AccountStatus.ACTIVE);
        when(account.getCurrencyCode()).thenReturn("INR");
        when(account.getAvailableBalance()).thenReturn(BigDecimal.TEN);
        return account;
    }

    private String requestBody() {
        return "{\"accountNumber\":\"ACC-1\",\"customerId\":\"customer-1\",\"productId\":\"product-1\","
                + "\"ownershipType\":\"INDIVIDUAL\",\"status\":\"ACTIVE\",\"currencyCode\":\"INR\",\"availableBalance\":10.00}";
    }
}
