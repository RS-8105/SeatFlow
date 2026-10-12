package com.ticketbooking.exception;

public class SeatAlreadyHeldException extends RuntimeException {
    public SeatAlreadyHeldException(String message) {
        super(message);
    }
}
