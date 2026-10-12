package com.ticketbooking.dto;

import com.ticketbooking.domain.PaymentStatus;

import java.time.Instant;

public record PaymentResponse(
        Long id,
        Long bookingId,
        PaymentStatus status,
        boolean simulated,
        Instant createdAt
) {
}
