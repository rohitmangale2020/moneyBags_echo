package com.training.platform.transactions.controller;

import com.training.platform.transactions.dto.StatementRequest;
import com.training.platform.transactions.dto.StatementResponse;
import com.training.platform.transactions.entity.AccountStatement;
import com.training.platform.transactions.service.AccountStatementService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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
    public List<StatementResponse> getByAccountId(@RequestParam String accountId) {
        return statementService.getByAccountId(accountId).stream().map(StatementResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StatementResponse record(@Valid @RequestBody StatementRequest request) {
        AccountStatement statement = new AccountStatement();
        statement.setAccountId(request.accountId());
        statement.setEntryType(request.entryType());
        statement.setAmount(request.amount());
        statement.setCurrencyCode(request.currencyCode().toUpperCase());
        statement.setBalanceAfter(request.balanceAfter());
        return StatementResponse.from(statementService.record(request.transactionId(), statement));
    }
}
