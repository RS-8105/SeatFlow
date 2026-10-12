package com.ticketbooking.dto;

import java.time.Instant;

public record SeatStateEvent(
        Long showtimeId,
        Long seatId,
        String status,
        Instant expiresAt
) {
}
