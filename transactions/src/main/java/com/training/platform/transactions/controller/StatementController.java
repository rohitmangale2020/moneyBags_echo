package com.training.platform.transactions.controller;

import com.training.platform.transactions.dto.StatementRequest;
import com.training.platform.transactions.dto.StatementResponse;
import com.training.platform.transactions.entity.AccountStatement;
import com.training.platform.transactions.entity.StatementEntryType;
import com.training.platform.transactions.entity.TransactionChannel;
import com.training.platform.transactions.service.AccountStatementService;
import jakarta.validation.Valid;
import java.util.List;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;

@RestController
@RequestMapping("/api/statements")
public class StatementController {
    private final AccountStatementService statementService;

    public StatementController(AccountStatementService statementService) { this.statementService = statementService; }

    @GetMapping("/{statementId}")
    public StatementResponse getById(@PathVariable String statementId) {
        return StatementResponse.from(statementService.getById(statementId));
    }

    @GetMapping
    public List<StatementResponse> getByAccountId(
            @RequestParam String accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) StatementEntryType entryType,
            @RequestParam(required = false) TransactionChannel channel) {
        return statementService.search(accountId, fromDate, toDate, entryType, channel)
                .stream().map(StatementResponse::from).toList();
    }

    @GetMapping("/monthly")
    public List<StatementResponse> getMonthlyStatement(
            @RequestParam String accountId,
            @RequestParam int year,
            @RequestParam int month) {
        return statementService.getMonthlyStatement(accountId, year, month)
                .stream()
                .map(StatementResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StatementResponse record(@Valid @RequestBody StatementRequest request) {
        AccountStatement statement = new AccountStatement();
        statement.setAccountId(request.accountId());
        statement.setDescription(request.description());
        statement.setEntryType(request.entryType());
        statement.setAmount(request.amount());
        statement.setCurrencyCode(request.currencyCode().toUpperCase());
        statement.setBalanceAfter(request.balanceAfter());
        return StatementResponse.from(statementService.record(request.transactionId(), statement));
    }
}
