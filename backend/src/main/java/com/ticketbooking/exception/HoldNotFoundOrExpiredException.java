package com.ticketbooking.exception;

public class HoldNotFoundOrExpiredException extends RuntimeException {
    public HoldNotFoundOrExpiredException(String message) {
        super(message);
    }
}
