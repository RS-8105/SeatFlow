package com.ticketbooking.dto;

import java.time.Instant;

public record ShowtimeResponse(
        Long id,
        Long eventId,
        String eventName,
        Long venueId,
        String venueName,
        String venueCity,
        Instant startsAt
) {
}
