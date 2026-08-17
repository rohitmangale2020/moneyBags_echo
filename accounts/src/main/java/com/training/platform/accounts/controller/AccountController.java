package com.training.platform.accounts.controller;

import com.training.platform.accounts.dto.AccountRequest;
import com.training.platform.accounts.dto.AccountResponse;
import com.training.platform.accounts.dto.AccountAdjustmentRequest;
import com.training.platform.accounts.dto.AccountAdjustmentResponse;
import com.training.platform.accounts.dto.AccountTransferRequest;
import com.training.platform.accounts.dto.AccountTransferResponse;
import com.training.platform.accounts.entity.Account;
import com.training.platform.accounts.entity.AccountStatus;
import com.training.platform.accounts.entity.OwnershipType;
import com.training.platform.accounts.service.AccountService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.Map;

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
    public Object find(@RequestParam(required = false) String customerId,
                       @RequestParam(required = false) String accountNumber,
                       @RequestParam(required = false) AccountStatus status,
                       @RequestParam(required = false) OwnershipType ownershipType,
                       @RequestParam(required = false) String currencyCode,
                       @RequestParam(required = false) Integer page,
                       @RequestParam(required = false) Integer size) {
        if (accountNumber != null) return List.of(AccountResponse.from(accountService.getByAccountNumber(accountNumber)));
        boolean hasFilters = status != null || ownershipType != null || (currencyCode != null && !currencyCode.isBlank());
        if (page != null || size != null || hasFilters) {
            int safePage = Math.max(0, page == null ? 0 : page);
            int safeSize = Math.min(100, Math.max(1, size == null ? 10 : size));
            Page<AccountResponse> accounts = accountService.getAccounts(
                    PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")),
                    customerId, status, ownershipType, currencyCode).map(AccountResponse::from);
            return accounts;
        }
        if (customerId != null) return accountService.getByCustomerId(customerId).stream().map(AccountResponse::from).toList();
        return accountService.getAllAccounts().stream().map(AccountResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@Valid @RequestBody AccountRequest request) {
        return AccountResponse.from(accountService.create(toEntity(request, currentUserId())));
    }

    @PutMapping("/{accountId}")
    public AccountResponse update(@PathVariable String accountId, @Valid @RequestBody AccountRequest request) {
        return AccountResponse.from(accountService.update(accountId, toEntity(request, currentUserId())));
    }

    @PostMapping("/transfers")
    @PreAuthorize("#request.effectivePurpose().name() == 'STANDARD' or hasAnyRole('SYSTEM','ADMIN')")
    public AccountTransferResponse transfer(@Valid @RequestBody AccountTransferRequest request) {
        return accountService.transfer(request);
    }

    @PostMapping("/product-rules/refresh")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Integer> refreshProductRules() {
        return Map.of("updatedAccounts", accountService.backfillMissingProductRules());
    }

    @PostMapping("/{accountId}/adjustments")
    @PreAuthorize("#request.adjustmentType().name() == 'OPENING_DEPOSIT' or #request.adjustmentType().name() == 'DEPOSIT' or #request.adjustmentType().name() == 'WITHDRAWAL' or hasAnyRole('SYSTEM','ADMIN')")
    public AccountAdjustmentResponse adjust(@PathVariable String accountId,
                                            @Valid @RequestBody AccountAdjustmentRequest request) {
        return accountService.adjust(accountId, request);
    }

    private Account toEntity(AccountRequest request, String userId) {
        Account account = new Account();
        account.setAccountNumber(request.accountNumber());
        account.setCustomerId(request.customerId());
        account.setProductId(request.productId());
        account.setOwnershipType(request.ownershipType());
        account.setStatus(request.status());
        account.setCurrencyCode(request.currencyCode().toUpperCase());
        account.setAvailableBalance(request.availableBalance());
        account.setClosedAt(request.closedAt());
        account.setCreatedByUserId(userId);
        account.setUpdatedByUserId(userId);
        return account;
    }

    private String currentUserId() {
        Authentication authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            throw new IllegalStateException("An authenticated JWT user is required");
        }
        Object userId = jwtAuthentication.getToken().getClaim("userId");
        String value = userId == null ? jwtAuthentication.getToken().getSubject()
                : String.valueOf(userId);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("JWT does not identify the authenticated user");
        }
        // Tokens issued before userId was introduced still identify the user by
        // subject. The database column is intentionally retained at 36 chars.
        return value.length() <= 36 ? value : value.substring(0, 36);
    }
}
