package com.training.platform.accounts.client;

/** Minimal fixed-deposit dependency returned by the transactions service. */
public record FixedDepositDependencyResponse(
        String contractId,
        String fdAccountId,
        String fundingAccountId,
        String payoutAccountId,
        String status) { }
