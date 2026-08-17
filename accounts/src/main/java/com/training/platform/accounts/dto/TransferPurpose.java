package com.training.platform.accounts.dto;

/** Distinguishes ordinary transfers from controlled fixed-deposit lifecycle movements. */
public enum TransferPurpose {
    STANDARD,
    FIXED_DEPOSIT_FUNDING,
    FIXED_DEPOSIT_MATURITY,
    FIXED_DEPOSIT_PREMATURE_CLOSURE
}
