package com.ticketbooking.dto;

public record ResetDemoResponse(
        Long showtimeId,
        int releasedHoldsCount,
        String message
) {
}
