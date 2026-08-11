package com.training.platform.transactions.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.training.platform.transactions.dto.TransactionRequest;
import com.training.platform.transactions.entity.BankTransaction;
import com.training.platform.transactions.entity.TransactionStatus;
import com.training.platform.transactions.entity.TransactionType;
import com.training.platform.transactions.service.BankTransactionService;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TransactionController.class)
@AutoConfigureMockMvc(addFilters = false)
class TransactionControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private BankTransactionService transactionService;

    @Test
    void getsTransactionById() throws Exception {
        when(transactionService.getById("txn-1")).thenReturn(transaction("txn-1", "REF-1"));

        mockMvc.perform(get("/api/transactions/txn-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("txn-1"))
                .andExpect(jsonPath("$.transactionRef").value("REF-1"))
                .andExpect(jsonPath("$.transactionType").value("TRANSFER"))
                .andExpect(jsonPath("$.transactionStatus").value("INITIATED"));
    }

    @Test
    void returnsNotFoundWhenTransactionDoesNotExist() throws Exception {
        when(transactionService.getById("missing"))
                .thenThrow(new EntityNotFoundException("Transaction not found: missing"));

        mockMvc.perform(get("/api/transactions/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Transaction not found: missing"));
    }

    @Test
    void searchesByReferenceDebitAccountAndCreditAccount() throws Exception {
        BankTransaction transaction = transaction("txn-1", "REF-1");
        when(transactionService.getByReference("REF-1")).thenReturn(transaction);
        when(transactionService.getDebitAccountTransactions("account-a")).thenReturn(List.of(transaction));
        when(transactionService.getCreditAccountTransactions("account-b")).thenReturn(List.of(transaction));

        mockMvc.perform(get("/api/transactions").param("transactionRef", "REF-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].transactionId").value("txn-1"));
        mockMvc.perform(get("/api/transactions").param("debitAccountId", "account-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].debitAccountId").value("account-a"));
        mockMvc.perform(get("/api/transactions").param("creditAccountId", "account-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].creditAccountId").value("account-b"));
    }

    @Test
    void rejectsSearchWithoutAFilter() throws Exception {
        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        "Provide transactionRef, debitAccountId, or creditAccountId"));

        verifyNoInteractions(transactionService);
    }

    @Test
    void initiatesTransactionAndNormalizesCurrencyAndFee() throws Exception {
        when(transactionService.initiate(any(BankTransaction.class))).thenAnswer(invocation -> {
            BankTransaction transaction = invocation.getArgument(0);
            ReflectionTestUtils.setField(transaction, "transactionId", "txn-created");
            ReflectionTestUtils.setField(transaction, "initiatedAt", LocalDateTime.of(2025, 1, 1, 10, 0));
            return transaction;
        });

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest(null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionId").value("txn-created"))
                .andExpect(jsonPath("$.currencyCode").value("INR"))
                .andExpect(jsonPath("$.feeAmount").value(0));

        ArgumentCaptor<BankTransaction> captor = ArgumentCaptor.forClass(BankTransaction.class);
        verify(transactionService).initiate(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("INR", captor.getValue().getCurrencyCode());
        org.junit.jupiter.api.Assertions.assertEquals(BigDecimal.ZERO, captor.getValue().getFeeAmount());
    }

    @Test
    void updatesTransactionUsingThePathId() throws Exception {
        when(transactionService.update(eq("txn-1"), any(BankTransaction.class))).thenAnswer(invocation -> {
            BankTransaction transaction = invocation.getArgument(1);
            ReflectionTestUtils.setField(transaction, "transactionId", "txn-1");
            ReflectionTestUtils.setField(transaction, "initiatedAt", LocalDateTime.of(2025, 1, 1, 10, 0));
            return transaction;
        });

        mockMvc.perform(put("/api/transactions/txn-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest(new BigDecimal("1.25")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("txn-1"))
                .andExpect(jsonPath("$.feeAmount").value(1.25));

        verify(transactionService).update(eq("txn-1"), any(BankTransaction.class));
    }

    @Test
    void rejectsInvalidTransactionRequestBeforeCallingService() throws Exception {
        TransactionRequest invalid = new TransactionRequest(
                "", null, null, null, null, null,
                BigDecimal.ZERO, "INVALID", null, null, null,
                null, null, null);

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());

        verify(transactionService, never()).initiate(any());
    }

    private static TransactionRequest validRequest(BigDecimal fee) {
        return new TransactionRequest(
                "REF-1", TransactionType.TRANSFER, TransactionStatus.INITIATED,
                "account-a", "account-b", null, new BigDecimal("100.00"),
                "inr", fee, "customer-1", null, null, null, null);
    }

    private static BankTransaction transaction(String id, String reference) {
        BankTransaction transaction = new BankTransaction();
        ReflectionTestUtils.setField(transaction, "transactionId", id);
        ReflectionTestUtils.setField(transaction, "initiatedAt", LocalDateTime.of(2025, 1, 1, 10, 0));
        transaction.setTransactionRef(reference);
        transaction.setTransactionType(TransactionType.TRANSFER);
        transaction.setTransactionStatus(TransactionStatus.INITIATED);
        transaction.setDebitAccountId("account-a");
        transaction.setCreditAccountId("account-b");
        transaction.setAmount(new BigDecimal("100.00"));
        transaction.setCurrencyCode("INR");
        transaction.setFeeAmount(BigDecimal.ZERO);
        return transaction;
    }
}
