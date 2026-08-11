package com.training.platform.transactions.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.training.platform.transactions.dto.StatementRequest;
import com.training.platform.transactions.entity.AccountStatement;
import com.training.platform.transactions.entity.BankTransaction;
import com.training.platform.transactions.entity.StatementEntryType;
import com.training.platform.transactions.service.AccountStatementService;
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

@WebMvcTest(StatementController.class)
@AutoConfigureMockMvc(addFilters = false)
class StatementControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private AccountStatementService statementService;

    @Test
    void getsStatementById() throws Exception {
        when(statementService.getById("statement-1")).thenReturn(statement("statement-1"));

        mockMvc.perform(get("/api/statements/statement-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statementId").value("statement-1"))
                .andExpect(jsonPath("$.transactionId").value("txn-1"))
                .andExpect(jsonPath("$.entryType").value("DEBIT"));
    }

    @Test
    void getsStatementsByAccountInServiceOrder() throws Exception {
        when(statementService.getByAccountId("account-1"))
                .thenReturn(List.of(statement("statement-2"), statement("statement-1")));

        mockMvc.perform(get("/api/statements").param("accountId", "account-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].statementId").value("statement-2"))
                .andExpect(jsonPath("$[1].statementId").value("statement-1"));
    }

    @Test
    void recordsStatementAndNormalizesCurrency() throws Exception {
        when(statementService.record(eq("txn-1"), any(AccountStatement.class))).thenAnswer(invocation -> {
            AccountStatement statement = invocation.getArgument(1);
            BankTransaction transaction = new BankTransaction();
            ReflectionTestUtils.setField(transaction, "transactionId", "txn-1");
            statement.setTransaction(transaction);
            ReflectionTestUtils.setField(statement, "statementId", "statement-created");
            ReflectionTestUtils.setField(statement, "postedAt", LocalDateTime.of(2025, 1, 1, 10, 0));
            return statement;
        });
        StatementRequest request = new StatementRequest(
                "txn-1", "account-1", StatementEntryType.CREDIT,
                new BigDecimal("25.00"), "inr", new BigDecimal("1025.00"));

        mockMvc.perform(post("/api/statements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statementId").value("statement-created"))
                .andExpect(jsonPath("$.currencyCode").value("INR"))
                .andExpect(jsonPath("$.balanceAfter").value(1025.00));

        ArgumentCaptor<AccountStatement> captor = ArgumentCaptor.forClass(AccountStatement.class);
        verify(statementService).record(eq("txn-1"), captor.capture());
        assertEquals("INR", captor.getValue().getCurrencyCode());
        assertEquals(StatementEntryType.CREDIT, captor.getValue().getEntryType());
    }

    @Test
    void rejectsInvalidStatementBeforeCallingService() throws Exception {
        StatementRequest request = new StatementRequest(
                "", "", null, BigDecimal.ZERO, "INVALID", null);

        mockMvc.perform(post("/api/statements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(statementService, never()).record(anyString(), any());
    }

    private static AccountStatement statement(String statementId) {
        BankTransaction transaction = new BankTransaction();
        ReflectionTestUtils.setField(transaction, "transactionId", "txn-1");
        AccountStatement statement = new AccountStatement();
        ReflectionTestUtils.setField(statement, "statementId", statementId);
        ReflectionTestUtils.setField(statement, "postedAt", LocalDateTime.of(2025, 1, 1, 10, 0));
        statement.setTransaction(transaction);
        statement.setAccountId("account-1");
        statement.setEntryType(StatementEntryType.DEBIT);
        statement.setAmount(new BigDecimal("25.00"));
        statement.setCurrencyCode("INR");
        statement.setBalanceAfter(new BigDecimal("975.00"));
        return statement;
    }
}
