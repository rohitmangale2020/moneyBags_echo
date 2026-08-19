package com.training.platform.transactions.dto;

import jakarta.validation.constraints.NotNull;

/** An administrator's decision for a risk-held transaction. */
public record TransactionApprovalRequest(@NotNull Decision decision, String note) {
    public enum Decision { APPROVE, REJECT }
}
