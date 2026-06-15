package com.eventledger.gateway.exception;

public class AccountServiceUnavailableException extends RuntimeException {

    public AccountServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public AccountServiceUnavailableException(String message) {
        super(message);
    }
}
