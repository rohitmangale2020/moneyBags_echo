package com.training.platform.transactions.controller;

import com.training.platform.transactions.dto.LedgerAccountRequest;
import com.training.platform.transactions.dto.LedgerAccountResponse;
import com.training.platform.transactions.dto.LedgerEntryResponse;
import com.training.platform.transactions.dto.LedgerPostingRequest;
import com.training.platform.transactions.service.LedgerService;
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
@RequestMapping("/api/ledger")
public class LedgerController {
    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) { this.ledgerService = ledgerService; }

    @GetMapping("/accounts")
    public List<LedgerAccountResponse> accounts() {
        return ledgerService.accounts().stream().map(LedgerAccountResponse::from).toList();
    }

    @GetMapping("/accounts/{code}")
    public LedgerAccountResponse account(@PathVariable String code) {
        return LedgerAccountResponse.from(ledgerService.account(code));
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public LedgerAccountResponse createAccount(@Valid @RequestBody LedgerAccountRequest request) {
        return LedgerAccountResponse.from(ledgerService.createAccount(request));
    }

    @GetMapping("/entries")
    public List<LedgerEntryResponse> entries(@RequestParam(required = false) String transactionRef,
                                             @RequestParam(required = false) String accountCode) {
        return ledgerService.entries(transactionRef, accountCode).stream().map(LedgerEntryResponse::from).toList();
    }

    @PostMapping("/entries")
    @ResponseStatus(HttpStatus.CREATED)
    public List<LedgerEntryResponse> post(@Valid @RequestBody LedgerPostingRequest request) {
        return ledgerService.post(request).stream().map(LedgerEntryResponse::from).toList();
    }
}
