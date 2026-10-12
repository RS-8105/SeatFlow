package com.ticketbooking.dto;

public record AuthResponse(
        String token,
        String tokenType,
        String userId,
        String username,
        String email,
        long expiresInMs
) {
}
