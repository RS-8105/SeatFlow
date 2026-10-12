package com.ticketbooking.dto;

public record RaceAttemptResult(
        String userId,
        boolean acquired,
        long latencyMs,
        String error
) {
    public String status() {
        if (error != null && !error.isBlank()) {
            return "ERROR";
        }
        return acquired ? "ACQUIRED" : "REJECTED";
    }
}
