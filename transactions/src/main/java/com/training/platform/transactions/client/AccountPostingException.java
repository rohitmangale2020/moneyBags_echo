package com.training.platform.transactions.client;

/** A controlled failure returned by, or encountered while calling, the accounts service. */
public class AccountPostingException extends RuntimeException {
    private final String failureCode;

    public AccountPostingException(String failureCode, String message) {
        super(message);
        this.failureCode = failureCode;
    }

    public String getFailureCode() { return failureCode; }
}
