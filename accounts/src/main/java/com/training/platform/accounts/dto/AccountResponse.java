package com.training.platform.accounts.dto;

import com.training.platform.accounts.entity.Account;
import com.training.platform.accounts.entity.AccountStatus;
import com.training.platform.accounts.entity.OwnershipType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AccountResponse(
        String accountId, String accountNumber, String customerId, String productId,
        OwnershipType ownershipType, AccountStatus status, String currencyCode,
        BigDecimal availableBalance, LocalDateTime openedAt, LocalDateTime closedAt,
        Long versionNo, LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(account.getAccountId(), account.getAccountNumber(), account.getCustomerId(),
                account.getProductId(), account.getOwnershipType(), account.getStatus(), account.getCurrencyCode(),
                account.getAvailableBalance(), account.getOpenedAt(), account.getClosedAt(), account.getVersionNo(),
                account.getCreatedAt(), account.getUpdatedAt());
    }
}
