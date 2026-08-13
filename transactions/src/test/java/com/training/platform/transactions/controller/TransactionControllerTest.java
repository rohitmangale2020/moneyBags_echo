package com.training.platform.transactions.controller;

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

import com.training.platform.transactions.entity.BankTransaction;
import com.training.platform.transactions.entity.TransactionStatus;
import com.training.platform.transactions.entity.TransactionType;
import com.training.platform.transactions.service.BankTransactionService;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TransactionControllerTest {
    private BankTransactionService transactionService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        transactionService = mock(BankTransactionService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new TransactionController(transactionService))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        authenticateUser();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getsTransactionById() throws Exception {
        BankTransaction transaction = transaction("TXN-1");
        when(transactionService.getById("transaction-1")).thenReturn(transaction);
        mockMvc.perform(get("/api/transactions/transaction-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionRef").value("TXN-1"));
    }

    @Test
    void findsTransactionByReferenceAndDebitAccount() throws Exception {
        BankTransaction transaction = transaction("TXN-1");
        when(transactionService.getByReference("TXN-1")).thenReturn(transaction);
        when(transactionService.getDebitAccountTransactions("account-1")).thenReturn(List.of(transaction));

        mockMvc.perform(get("/api/transactions").param("transactionRef", "TXN-1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get("/api/transactions").param("debitAccountId", "account-1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].debitAccountId").value("account-1"));
    }

    @Test
    void getsAllTransactionsWhenNoFilterIsProvided() throws Exception {
        BankTransaction first = transaction("TXN-1");
        BankTransaction second = transaction("TXN-2");
        when(transactionService.getAllTransactions()).thenReturn(List.of(first, second));

        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].transactionRef").value("TXN-1"))
                .andExpect(jsonPath("$[1].transactionRef").value("TXN-2"));

        verify(transactionService).getAllTransactions();
    }

    @Test
    void createsAndUpdatesTransaction() throws Exception {
        BankTransaction transaction = transaction("TXN-1");
        when(transactionService.initiate(any(BankTransaction.class))).thenReturn(transaction);
        when(transactionService.update(org.mockito.ArgumentMatchers.eq("transaction-1"), any(BankTransaction.class))).thenReturn(transaction);

        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content(requestBody()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.transactionId").value("transaction-1"));
        mockMvc.perform(put("/api/transactions/transaction-1").contentType(MediaType.APPLICATION_JSON).content(requestBody()))
                .andExpect(status().isOk());
        verify(transactionService).initiate(any(BankTransaction.class));
        verify(transactionService).update(org.mockito.ArgumentMatchers.eq("transaction-1"), any(BankTransaction.class));
    }

    @Test
    void rejectsInvalidPayload() throws Exception {
        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsNotFoundFromService() throws Exception {
        when(transactionService.getById("missing")).thenThrow(new EntityNotFoundException("Transaction not found: missing"));
        mockMvc.perform(get("/api/transactions/missing"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
    }

    private BankTransaction transaction(String reference) {
        BankTransaction transaction = mock(BankTransaction.class);
        when(transaction.getTransactionId()).thenReturn("transaction-1");
        when(transaction.getTransactionRef()).thenReturn(reference);
        when(transaction.getTransactionType()).thenReturn(TransactionType.TRANSFER);
        when(transaction.getTransactionStatus()).thenReturn(TransactionStatus.INITIATED);
        when(transaction.getDebitAccountId()).thenReturn("account-1");
        when(transaction.getAmount()).thenReturn(BigDecimal.TEN);
        when(transaction.getCurrencyCode()).thenReturn("INR");
        when(transaction.getFeeAmount()).thenReturn(BigDecimal.ZERO);
        return transaction;
    }

    private String requestBody() {
        return "{\"transactionRef\":\"TXN-1\",\"transactionType\":\"TRANSFER\",\"transactionStatus\":\"INITIATED\","
                + "\"debitAccountId\":\"account-1\",\"creditAccountId\":\"account-2\",\"amount\":10.00,\"currencyCode\":\"INR\"}";
    }

    private void authenticateUser() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("userId")).thenReturn(1L);
        JwtAuthenticationToken authentication = mock(JwtAuthenticationToken.class);
        when(authentication.getToken()).thenReturn(jwt);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
