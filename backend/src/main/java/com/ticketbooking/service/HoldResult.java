package com.ticketbooking.service;

import java.time.Instant;
import java.util.Set;

public record HoldResult(
        String holdToken,
        Long showtimeId,
        Set<Long> seatIds,
        String userId,
        Instant expiresAt
) {
}
