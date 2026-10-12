package com.ticketbooking.dto;

import jakarta.validation.constraints.NotBlank;

public record PaymentRequest(
        String userId,

        Boolean simulateSuccess
) {
}
