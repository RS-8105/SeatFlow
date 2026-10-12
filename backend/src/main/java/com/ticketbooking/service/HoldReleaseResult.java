package com.ticketbooking.service;

import java.util.Set;

public record HoldReleaseResult(
        String holdToken,
        Long showtimeId,
        Set<Long> releasedSeatIds,
        boolean fullyReleased
) {
}
