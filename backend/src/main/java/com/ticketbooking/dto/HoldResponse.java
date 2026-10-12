package com.ticketbooking.dto;

import java.time.Instant;
import java.util.Set;

public record HoldResponse(
        String holdToken,
        Long showtimeId,
        Set<Long> seatIds,
        String userId,
        Instant expiresAt
) {
}
