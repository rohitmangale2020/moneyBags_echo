package com.training.platform.accounts.controller;

import com.training.platform.accounts.dto.InterestBearingAccountResponse;
import com.training.platform.accounts.dto.AnnualFeeAccountResponse;
import com.training.platform.accounts.service.AccountService;
import com.training.platform.accounts.dto.InterestProcessingRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/internal/accounts")
@PreAuthorize("hasAnyRole('SYSTEM','ADMIN')")
public class InternalAccountController {
    private final AccountService accountService;

    public InternalAccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/interest-due")
    public List<InterestBearingAccountResponse> interestDue(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return accountService.interestDue(asOf).stream()
                .map(InterestBearingAccountResponse::from)
                .toList();
    }

    @GetMapping("/annual-fees")
    public List<AnnualFeeAccountResponse> annualFeeAccounts() {
        return accountService.annualFeeAccounts();
    }

    @PostMapping("/{accountId}/interest-processed")
    public InterestBearingAccountResponse markInterestProcessed(
            @PathVariable String accountId,
            @Valid @RequestBody InterestProcessingRequest request) {
        return InterestBearingAccountResponse.from(
                accountService.markInterestProcessed(accountId, request.periodEnd(), request.transactionRef()));
    }
}
