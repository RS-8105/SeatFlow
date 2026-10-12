package com.ticketbooking.dto;

import java.time.Instant;

public record EventResponse(
        Long id,
        String name,
        String description,
        Instant createdAt
) {
}
