package com.training.platform.transactions.dto;

import com.training.platform.transactions.entity.LedgerAccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LedgerAccountRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_\\-]{2,60}") String code,
        @NotBlank @Size(max = 160) String name,
        @NotNull LedgerAccountType accountType) { }
