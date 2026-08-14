package com.training.platform.transactions.dto;

import com.training.platform.transactions.entity.LedgerEntry;
import com.training.platform.transactions.entity.LedgerEntryStatus;
import com.training.platform.transactions.entity.LedgerEntryType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record LedgerEntryResponse(String id, String transactionRef, int lineNumber,
                                  String ledgerAccountCode, String customerAccountId,
                                  LedgerEntryType entryType, BigDecimal amount,
                                  String currencyCode, LocalDate postingDate,
                                  String description, LedgerEntryStatus status) {
    public static LedgerEntryResponse from(LedgerEntry entry) {
        return new LedgerEntryResponse(entry.getLedgerEntryId(), entry.getTransactionRef(), entry.getLineNumber(),
                entry.getLedgerAccount().getCode(), entry.getCustomerAccountId(), entry.getEntryType(),
                entry.getAmount(), entry.getCurrencyCode(), entry.getPostingDate(), entry.getDescription(),
                entry.getStatus());
    }
}
