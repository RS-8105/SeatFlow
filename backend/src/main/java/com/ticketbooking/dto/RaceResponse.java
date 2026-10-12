package com.ticketbooking.dto;

import java.util.List;

public record RaceResponse(
        Long showtimeId,
        Long seatId,
        int requestedUsers,
        int successfulHolds,
        int rejectedHolds,
        int errorCount,
        double p50LatencyMs,
        double p95LatencyMs,
        double p99LatencyMs,
        int confirmedBookingsInDb,
        List<RaceAttemptResult> attempts
) {
    public int totalAttempts() {
        return requestedUsers;
    }

    public int failedAttempts() {
        return rejectedHolds;
    }

    public double oversellPercentage() {
        if (confirmedBookingsInDb > 1) {
            return ((double) (confirmedBookingsInDb - 1) / Math.max(1, requestedUsers)) * 100.0;
        }
        if (successfulHolds > 1) {
            return ((double) (successfulHolds - 1) / Math.max(1, requestedUsers)) * 100.0;
        }
        return 0.0;
    }
}
