package com.ticketbooking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateHoldRequest(
        @NotEmpty(message = "Seat IDs collection must not be empty")
        List<Long> seatIds,

        String userId
) {
}
