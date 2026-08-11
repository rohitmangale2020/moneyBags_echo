package com.training.platform.accounts.controller;

import com.training.platform.accounts.dto.AccountRequest;
import com.training.platform.accounts.dto.AccountResponse;
import com.training.platform.accounts.dto.AccountTransferRequest;
import com.training.platform.accounts.dto.AccountTransferResponse;
import com.training.platform.accounts.entity.Account;
import com.training.platform.accounts.service.AccountService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) { this.accountService = accountService; }

    @GetMapping("/{accountId}")
    public AccountResponse getById(@PathVariable String accountId) {
        return AccountResponse.from(accountService.getById(accountId));
    }

    @GetMapping
    public List<AccountResponse> find(@RequestParam(required = false) String customerId,
                                      @RequestParam(required = false) String accountNumber) {
        if (customerId != null) return accountService.getByCustomerId(customerId).stream().map(AccountResponse::from).toList();
        if (accountNumber != null) return List.of(AccountResponse.from(accountService.getByAccountNumber(accountNumber)));
        throw new IllegalArgumentException("Provide customerId or accountNumber");
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@Valid @RequestBody AccountRequest request) {
        return AccountResponse.from(accountService.create(toEntity(request)));
    }

    @PutMapping("/{accountId}")
    public AccountResponse update(@PathVariable String accountId, @Valid @RequestBody AccountRequest request) {
        return AccountResponse.from(accountService.update(accountId, toEntity(request)));
    }

    @PostMapping("/transfers")
    public AccountTransferResponse transfer(@Valid @RequestBody AccountTransferRequest request) {
        return accountService.transfer(request);
    }

    private Account toEntity(AccountRequest request) {
        Account account = new Account();
        account.setAccountNumber(request.accountNumber());
        account.setCustomerId(request.customerId());
        account.setProductId(request.productId());
        account.setOwnershipType(request.ownershipType());
        account.setStatus(request.status());
        account.setCurrencyCode(request.currencyCode().toUpperCase());
        account.setAvailableBalance(request.availableBalance());
        account.setClosedAt(request.closedAt());
        return account;
    }
}
