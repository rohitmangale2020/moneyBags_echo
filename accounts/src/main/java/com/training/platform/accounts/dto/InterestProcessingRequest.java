package com.training.platform.accounts.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record InterestProcessingRequest(
        @NotNull LocalDate periodEnd,
        @NotBlank String transactionRef) { }
