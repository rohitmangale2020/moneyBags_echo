package com.training.platform.transactions.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.training.platform.transactions.entity.AccountStatement;
import com.training.platform.transactions.entity.BankTransaction;
import com.training.platform.transactions.entity.StatementEntryType;
import com.training.platform.transactions.service.AccountStatementService;
import java.math.BigDecimal;
import java.util.List;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StatementControllerTest {
    private AccountStatementService statementService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        statementService = mock(AccountStatementService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new StatementController(statementService))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void getsAndFindsStatements() throws Exception {
        AccountStatement statement = statement();
        when(statementService.getById("statement-1")).thenReturn(statement);
        when(statementService.search(org.mockito.ArgumentMatchers.eq("account-1"),
                any(LocalDate.class), any(LocalDate.class),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(List.of(statement));

        mockMvc.perform(get("/api/statements/statement-1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statementId").value("statement-1"));
        mockMvc.perform(get("/api/statements").param("accountId", "account-1")
                        .param("fromDate", "2026-08-01").param("toDate", "2026-08-31"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void recordsStatementAndRejectsInvalidPayload() throws Exception {
        AccountStatement statement = statement();
        when(statementService.record(org.mockito.ArgumentMatchers.eq("transaction-1"), any(AccountStatement.class)))
                .thenReturn(statement);

        mockMvc.perform(post("/api/statements").contentType(MediaType.APPLICATION_JSON).content(requestBody()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.accountId").value("account-1"));
        mockMvc.perform(post("/api/statements").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    private AccountStatement statement() {
        BankTransaction transaction = mock(BankTransaction.class);
        when(transaction.getTransactionId()).thenReturn("transaction-1");
        when(transaction.getTransactionRef()).thenReturn("REF-1");
        when(transaction.getTransactionType()).thenReturn(com.training.platform.transactions.entity.TransactionType.WITHDRAWAL);
        AccountStatement statement = mock(AccountStatement.class);
        when(statement.getStatementId()).thenReturn("statement-1");
        when(statement.getTransaction()).thenReturn(transaction);
        when(statement.getAccountId()).thenReturn("account-1");
        when(statement.getEntryType()).thenReturn(StatementEntryType.DEBIT);
        when(statement.getAmount()).thenReturn(BigDecimal.TEN);
        when(statement.getCurrencyCode()).thenReturn("INR");
        when(statement.getBalanceAfter()).thenReturn(BigDecimal.valueOf(90));
        return statement;
    }

    private String requestBody() {
        return "{\"transactionId\":\"transaction-1\",\"accountId\":\"account-1\",\"entryType\":\"DEBIT\","
                + "\"amount\":10.00,\"currencyCode\":\"INR\",\"balanceAfter\":90.00}";
    }
}
