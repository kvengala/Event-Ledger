package com.eventledger.gateway.exception;

public class EventNotFoundException extends RuntimeException {

    public EventNotFoundException(String id) {
        super("Event not found: " + id);
    }
}
