package com.training.platform.users.exception;

public class CurrentPasswordMismatchException extends RuntimeException {

    public CurrentPasswordMismatchException() {
        super("The current password is incorrect");
    }
}
