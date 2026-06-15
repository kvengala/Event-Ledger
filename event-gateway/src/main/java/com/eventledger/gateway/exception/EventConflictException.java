package com.eventledger.gateway.exception;

public class EventConflictException extends RuntimeException {

    public EventConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
