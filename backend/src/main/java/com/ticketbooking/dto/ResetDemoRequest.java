package com.ticketbooking.dto;

import jakarta.validation.constraints.NotNull;

public record ResetDemoRequest(
        @NotNull(message = "Showtime ID must not be null")
        Long showtimeId
) {
}
