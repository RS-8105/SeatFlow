package com.ticketbooking.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Username or email must not be blank")
        String username,

        @NotBlank(message = "Password must not be blank")
        String password
) {
}
