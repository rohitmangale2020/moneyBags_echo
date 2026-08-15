package com.training.platform.transactions.dto;

import com.training.platform.transactions.entity.LedgerEntryType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record LedgerPostingRequest(
        @NotBlank @Size(max = 40) String transactionRef,
        LocalDate postingDate,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currencyCode,
        @Size(max = 500) String description,
        @NotEmpty @Valid List<Item> items) {
    public record Item(
            @NotBlank @Size(max = 60) String ledgerAccountCode,
            @Size(max = 36) String customerAccountId,
            @NotNull LedgerEntryType entryType,
            @NotNull @DecimalMin(value = "0.0001") BigDecimal amount,
            @Size(max = 500) String description) { }
}
