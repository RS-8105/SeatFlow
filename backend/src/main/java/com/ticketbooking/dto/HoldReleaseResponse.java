package com.ticketbooking.dto;

import java.util.Set;

public record HoldReleaseResponse(
        String holdToken,
        Long showtimeId,
        Set<Long> releasedSeatIds,
        boolean fullyReleased
) {
}
