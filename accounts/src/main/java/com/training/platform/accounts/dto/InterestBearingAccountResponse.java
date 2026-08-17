package com.training.platform.accounts.dto;

import com.training.platform.accounts.entity.Account;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Data required by the transactions service to create a monthly interest posting. */
public record InterestBearingAccountResponse(
        String accountId,
        String customerId,
        String productId,
        String productTypeCode,
        String currencyCode,
        BigDecimal balance,
        BigDecimal annualInterestRate,
        LocalDate accruedThrough,
        LocalDate payoutDueDate) {

    public static InterestBearingAccountResponse from(Account account) {
        return new InterestBearingAccountResponse(account.getAccountId(), account.getCustomerId(),
                account.getProductId(), account.getProductTypeCode(), account.getCurrencyCode(),
                account.getAvailableBalance(), account.getAnnualInterestRate(),
                account.getInterestAccruedThrough(), account.getNextInterestPayoutDate());
    }
}
