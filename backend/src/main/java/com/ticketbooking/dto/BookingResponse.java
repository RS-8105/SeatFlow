package com.ticketbooking.dto;

import com.ticketbooking.domain.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record BookingResponse(
        Long id,
        String userId,
        Long showtimeId,
        String eventName,
        String venueName,
        String venueCity,
        Instant showtimeStartsAt,
        BookingStatus status,
        String idempotencyKey,
        BigDecimal totalAmount,
        Instant createdAt,
        List<Long> seatIds,
        List<BookingSeatDetail> seats
) {
    public BookingResponse(
            Long id,
            String userId,
            Long showtimeId,
            BookingStatus status,
            String idempotencyKey,
            BigDecimal totalAmount,
            Instant createdAt,
            List<Long> seatIds
    ) {
        this(id, userId, showtimeId, null, null, null, null, status, idempotencyKey, totalAmount, createdAt, seatIds, List.of());
    }

    public record BookingSeatDetail(
            Long seatId,
            String rowLabel,
            Integer seatNumber,
            String seatLabel,
            String category,
            BigDecimal price
    ) {
    }
}
