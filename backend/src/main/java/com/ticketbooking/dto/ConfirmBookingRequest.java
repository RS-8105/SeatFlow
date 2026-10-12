package com.ticketbooking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ConfirmBookingRequest(
        @NotNull(message = "Showtime ID must not be null")
        Long showtimeId,

        @NotEmpty(message = "Seat IDs collection must not be empty")
        List<Long> seatIds,

        @NotBlank(message = "Hold token must not be blank")
        String holdToken,

        String userId
) {
}
