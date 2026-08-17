package com.training.platform.accounts.dto;

import com.training.platform.accounts.entity.Account;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Active savings/current account eligible for its configured anniversary fee. */
public record AnnualFeeAccountResponse(
        String accountId,
        String customerId,
        String productId,
        String productTypeCode,
        String currencyCode,
        BigDecimal balance,
        LocalDateTime openedAt,
        BigDecimal annualMaintenanceFee) {

    public static AnnualFeeAccountResponse from(Account account, BigDecimal fee) {
        return new AnnualFeeAccountResponse(account.getAccountId(), account.getCustomerId(),
                account.getProductId(), account.getProductTypeCode(), account.getCurrencyCode(),
                account.getAvailableBalance(), account.getOpenedAt(), fee);
    }
}
