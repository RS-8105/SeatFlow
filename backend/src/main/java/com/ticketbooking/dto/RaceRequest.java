package com.ticketbooking.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RaceRequest(
        @NotNull(message = "Showtime ID must not be null")
        Long showtimeId,

        @NotNull(message = "Seat ID must not be null")
        Long seatId,

        @NotNull(message = "User count must not be null")
        @Min(value = 2, message = "User count must be at least 2")
        @Max(value = 200, message = "User count cannot exceed 200")
        Integer userCount
) {
}
