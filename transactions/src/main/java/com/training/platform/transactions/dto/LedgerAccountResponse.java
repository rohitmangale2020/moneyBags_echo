package com.training.platform.transactions.dto;

import com.training.platform.transactions.entity.LedgerAccount;
import com.training.platform.transactions.entity.LedgerAccountType;
import java.math.BigDecimal;

public record LedgerAccountResponse(String id, String code, String name,
                                    LedgerAccountType accountType, BigDecimal currentBalance,
                                    boolean active) {
    public static LedgerAccountResponse from(LedgerAccount account) {
        return new LedgerAccountResponse(account.getLedgerAccountId(), account.getCode(), account.getName(),
                account.getAccountType(), account.getCurrentBalance(), account.isActive());
    }
}
