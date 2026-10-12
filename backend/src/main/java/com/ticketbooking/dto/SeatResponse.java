package com.ticketbooking.dto;

import java.math.BigDecimal;

public record SeatResponse(
        Long id,
        String rowLabel,
        Integer seatNumber,
        String category,
        BigDecimal price,
        String status
) {
}
